package pt.vclubroom.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.converter.ByteArrayMessageConverter;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import pt.vclubroom.TestRequests;
import pt.vclubroom.TestcontainersConfiguration;
import pt.vclubroom.club.application.ClubService;
import pt.vclubroom.club.application.CreateClubCommand;
import pt.vclubroom.forum.application.CreatePostCommand;
import pt.vclubroom.forum.application.PostService;
import pt.vclubroom.identity.api.PlatformRole;
import pt.vclubroom.identity.application.SessionRevocationService;
import pt.vclubroom.identity.domain.User;
import pt.vclubroom.identity.persistence.UserRepository;
import pt.vclubroom.membership.api.ClubAuthorization;
import pt.vclubroom.realtime.api.RealtimePublisher;
import pt.vclubroom.shared.security.Actor;
import pt.vclubroom.space.api.ChannelType;
import pt.vclubroom.space.api.SpaceAccessPolicy;
import pt.vclubroom.space.domain.AccessRule;
import pt.vclubroom.space.domain.Space;
import pt.vclubroom.space.persistence.SpaceRepository;

/**
 * /ws end to end (E10-S01): a real server, the real login, a real STOMP client. The "other instance" is played by
 * writing straight to the Redis channel, which is all a second instance does.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class RealtimeIntegrationTests {

    private static final String ORIGIN = "http://localhost:5173";      // app.public-url in tests

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    UserRepository users;

    @Autowired
    ClubService clubs;

    @Autowired
    ClubAuthorization authorization;

    @Autowired
    SpaceAccessPolicy spaceAccess;

    @Autowired
    SpaceRepository spaces;

    @Autowired
    PostService posts;

    @Autowired
    RealtimePublisher realtime;

    @Autowired
    StringRedisTemplate redis;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    SessionRevocationService revocation;

    private User founder;
    private User stranger;
    private UUID channelId;          // the club's public Posts channel
    private UUID minutesId;          // a forum channel in a members-only space

    private User localUser(String name) {
        String login = name + "-" + TestRequests.suffix();
        return users.save(User.createLocal(login, name, login + "@example.test", "{noop}secret", PlatformRole.USER, Instant.now()));
    }

    @BeforeEach
    void aClubWithAPublicAndAMembersOnlyChannel() {
        founder = localUser("ada");
        stranger = localUser("sam");
        UUID clubId = clubs.requestCreation(new Actor(founder.id(), false),
            new CreateClubCommand("Chess " + TestRequests.suffix(), null, null, "Games", List.of())).id();
        clubs.approve(new Actor(UUID.randomUUID(), true), clubId);
        await().atMost(Duration.ofSeconds(10)).until(() -> authorization.isOwner(clubId, founder.id())
            && !spaceAccess.readableForumChannels(clubId, new Actor(founder.id(), false)).isEmpty());
        channelId = spaceAccess.readableForumChannels(clubId, new Actor(founder.id(), false)).iterator().next();
        Space board = Space.create(clubId, "Board room", null, 1, AccessRule.clubMembers(), AccessRule.clubMembers(), Instant.now());
        minutesId = board.addChannel(ChannelType.FORUM, "Minutes", Instant.now()).id();
        spaces.save(board);
    }

    /** Logs in like the SPA: the CSRF cookie first, then POST /api/auth/login; returns the SESSION cookie. */
    private String sessionOf(User user) {
        ResponseEntity<Void> csrf = rest.getForEntity("/api/auth/csrf", Void.class);
        String xsrf = cookie(csrf, "XSRF-TOKEN");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("X-XSRF-TOKEN", xsrf);
        headers.add(HttpHeaders.COOKIE, "XSRF-TOKEN=" + xsrf);
        ResponseEntity<String> login = rest.exchange("/api/auth/login", HttpMethod.POST,
            new HttpEntity<>("{\"username\":\"" + user.login() + "\",\"password\":\"secret\"}", headers), String.class);
        assertThat(login.getStatusCode().is2xxSuccessful()).as(login.getBody()).isTrue();
        return cookie(login, "SESSION");
    }

    private static String cookie(ResponseEntity<?> response, String name) {
        return response.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE).stream()
            .filter(c -> c.startsWith(name + "="))
            .map(c -> c.substring(name.length() + 1, c.indexOf(';')))
            .findFirst().orElseThrow(() -> new AssertionError("no " + name + " cookie"));
    }

    /** What one browser tab sees: the messages of its subscriptions, and the errors of its connection. */
    static final class Tab extends StompSessionHandlerAdapter {
        final BlockingQueue<Map<String, Object>> messages = new LinkedBlockingQueue<>();
        final BlockingQueue<String> errors = new LinkedBlockingQueue<>();
        StompSession session;

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return byte[].class;                                              // ERROR frames have an empty body
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            errors.add(String.valueOf(headers.getFirst("message")));
        }

        @Override
        public void handleTransportError(StompSession s, Throwable exception) {
            errors.add(exception.toString());
        }

        Tab subscribe(String destination) {
            session.subscribe(destination, new StompFrameHandler() {
                @Override
                public Type getPayloadType(StompHeaders headers) {
                    return Map.class;
                }

                @Override
                @SuppressWarnings("unchecked")
                public void handleFrame(StompHeaders headers, Object payload) {
                    messages.add((Map<String, Object>) payload);
                }
            });
            return this;
        }

        Map<String, Object> next() throws InterruptedException {
            return messages.poll(5, TimeUnit.SECONDS);
        }
    }

    private Tab connect(String session, String origin) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new CompositeMessageConverter(List.of(new MappingJackson2MessageConverter(), new ByteArrayMessageConverter())));
        WebSocketHttpHeaders handshake = new WebSocketHttpHeaders();
        handshake.setOrigin(origin);
        if (session != null) {
            handshake.add(HttpHeaders.COOKIE, "SESSION=" + session);
        }
        Tab tab = new Tab();
        tab.session = client.connectAsync("ws://localhost:" + port + "/ws", handshake, new StompHeaders(), tab).get(5, TimeUnit.SECONDS);
        return tab;
    }

    /** Subscriptions register asynchronously: probe until the topic delivers, then drain the probes. */
    private void awaitSubscribed(Tab tab, String destination) {
        await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(200)).until(() -> {
            realtime.toTopic(destination, "probe", Map.of());
            return tab.messages.poll(150, TimeUnit.MILLISECONDS) != null;
        });
        await().pollDelay(Duration.ofMillis(300)).until(() -> true);
        tab.messages.clear();
    }

    @Test
    void theHandshakeNeedsASessionAndAnAllowedOrigin() {
        assertThatThrownBy(() -> connect(null, ORIGIN)).hasStackTraceContaining("401");
        String session = sessionOf(founder);
        assertThatThrownBy(() -> connect(session, "https://evil.example")).hasStackTraceContaining("403");     // CSWSH
    }

    @Test
    void aReaderGetsNewPostsAfterTheCommitOnly() throws Exception {
        String topic = "/topic/channels/" + channelId + "/posts";
        Tab tab = connect(sessionOf(stranger), ORIGIN).subscribe(topic);                // a PUBLIC space: readable
        awaitSubscribed(tab, topic);

        tx.executeWithoutResult(status -> {
            realtime.toTopic(topic, "rolled.back", Map.of());
            status.setRollbackOnly();
        });
        var post = posts.createPost(new Actor(founder.id(), false), channelId, new CreatePostCommand(null, "Hello", List.of()));

        Map<String, Object> message = tab.next();
        assertThat(message).containsEntry("type", "post.created");                       // the rolled-back push never left
        assertThat(message.get("payload")).isEqualTo(Map.of("postId", post.id().toString()));
        assertThat(message).containsKey("sentAt");
    }

    @Test
    void unknownAndUnreadableDestinationsAreRefusedAndSoIsSend() throws Exception {
        String session = sessionOf(stranger);
        Tab unknown = connect(session, ORIGIN).subscribe("/topic/everything");
        assertThat(unknown.errors.poll(5, TimeUnit.SECONDS)).isNotNull();                   // fail closed

        Tab membersOnly = connect(session, ORIGIN).subscribe("/topic/channels/" + minutesId + "/posts");
        assertThat(membersOnly.errors.poll(5, TimeUnit.SECONDS)).isNotNull();

        Tab sender = connect(session, ORIGIN);
        sender.session.send("/app/anything", new byte[0]);                                 // push-only (R-03)
        assertThat(sender.errors.poll(5, TimeUnit.SECONDS)).isNotNull();
    }

    @Test
    void aPushFromAnotherInstanceArrives() throws Exception {
        String topic = "/topic/channels/" + channelId + "/posts";
        Tab tab = connect(sessionOf(founder), ORIGIN).subscribe(topic);
        awaitSubscribed(tab, topic);

        redis.convertAndSend("vclubroom:rt", """
            {"kind":"TOPIC","destination":"%s","envelope":{"type":"post.created","payload":{"postId":"x"},"sentAt":"2026-10-19T10:00:00Z"}}
            """.formatted(topic));

        assertThat(tab.next()).containsEntry("type", "post.created");
    }

    @Test
    void revokedSessionsStopReceivingPushes() throws Exception {
        String topic = "/topic/channels/" + minutesId + "/posts";                       // members only
        Tab tab = connect(sessionOf(founder), ORIGIN).subscribe(topic);
        awaitSubscribed(tab, topic);

        revocation.revokeAll(founder.id());                                              // what disabling does (BR-24)
        await().pollDelay(Duration.ofSeconds(1)).until(() -> true);                      // the session deletion reaches the sockets
        realtime.toTopic(topic, "post.created", Map.of("postId", "secret"));

        assertThat(tab.next()).isNull();
    }

    @Test
    void myOwnQueueReachesOnlyMe() throws Exception {
        Tab mine = connect(sessionOf(founder), ORIGIN).subscribe("/user/queue/notifications");
        Tab theirs = connect(sessionOf(stranger), ORIGIN).subscribe("/user/queue/notifications");
        await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(200)).until(() -> {
            realtime.toUser(founder.id(), "/queue/notifications", "notification.created", Map.of("n", 1));
            return mine.messages.poll(150, TimeUnit.MILLISECONDS) != null;
        });

        assertThat(theirs.messages.poll(1, TimeUnit.SECONDS)).isNull();
    }
}
