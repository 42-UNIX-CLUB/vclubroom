package pt.vclubroom.club;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pt.vclubroom.TestRequests.user;
import static pt.vclubroom.TestRequests.xsrf;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import pt.vclubroom.TestRequests;
import pt.vclubroom.TestcontainersConfiguration;
import pt.vclubroom.club.api.JoinPolicy;
import pt.vclubroom.club.application.ClubService;
import pt.vclubroom.club.application.CreateClubCommand;
import pt.vclubroom.club.events.ClubArchived;
import pt.vclubroom.club.persistence.ClubRepository;
import pt.vclubroom.membership.api.ClubAuthorization;
import pt.vclubroom.shared.security.Actor;
import pt.vclubroom.space.api.SpaceAccessPolicy;

/**
 * Archiving a club (E14-S01) across the modules: every write is refused, everything stays readable, and what was
 * waiting (a join request, an ownership invitation) is closed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class ArchiveClubIntegrationTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    ClubService clubs;

    @Autowired
    ClubRepository clubRepository;

    @Autowired
    ClubAuthorization authorization;

    @Autowired
    SpaceAccessPolicy spaceAccess;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    ApplicationEvents events;

    private final UUID admin = UUID.randomUUID();
    private final UUID founder = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private final UUID applicant = UUID.randomUUID();
    private String name;
    private String slug;
    private UUID clubId;
    private UUID channelId;
    private String postId;

    private void joinPolicy(JoinPolicy policy) {
        tx.executeWithoutResult(s -> clubRepository.findById(clubId).orElseThrow().changeJoinPolicy(policy, Instant.now()));
    }

    private ResultActions send(MockHttpServletRequestBuilder request, UUID as, String json) throws Exception {
        return mvc.perform(request.with(user(as, as.equals(admin))).with(xsrf(mvc)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** A club with a post and a comment, a member, a pending join request and a pending ownership invitation. */
    @BeforeEach
    void aLivelyClub() throws Exception {
        name = "Origami " + TestRequests.suffix();
        var club = clubs.requestCreation(new Actor(founder, false), new CreateClubCommand(name, null, null, "Arts", List.of()));
        clubId = club.id();
        slug = club.slug();
        clubs.approve(new Actor(admin, true), clubId);
        await().atMost(Duration.ofSeconds(10)).until(() ->
            authorization.isOwner(clubId, founder) && !spaceAccess.readableForumChannels(clubId, new Actor(founder, false)).isEmpty());
        channelId = spaceAccess.readableForumChannels(clubId, new Actor(founder, false)).iterator().next();

        joinPolicy(JoinPolicy.OPEN);
        send(post("/api/clubs/{id}/join", clubId), member, "{}").andExpect(status().isCreated());
        joinPolicy(JoinPolicy.APPROVAL);
        send(post("/api/clubs/{id}/join", clubId), applicant, "{}").andExpect(status().isAccepted());
        send(post("/api/clubs/{id}/ownership-invitations", clubId), founder,
            "{\"inviteeId\":\"" + member + "\",\"transfer\":false}").andExpect(status().isCreated());
        String body = send(post("/api/channels/{id}/posts", channelId), founder, "{\"body\":\"Crane tutorial\"}")
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        postId = JsonPath.read(body, "$.id");
        send(post("/api/posts/{id}/comments", postId), member, "{\"body\":\"Thanks!\"}").andExpect(status().isCreated());
    }

    private void archive() throws Exception {
        send(post("/api/admin/clubs/{id}/archive", clubId), admin, "{\"reason\":\"Inactive for a year\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ARCHIVED"))
            .andExpect(jsonPath("$.reviewNote").value("Inactive for a year"))
            .andExpect(jsonPath("$.eras[0].endedAt").value(notNullValue()));
    }

    @Test
    void anArchivedClubEndsItsEraAndClosesWhatWasWaiting() throws Exception {
        archive();

        assertThat(events.stream(ClubArchived.class).filter(e -> e.clubId().equals(clubId)))
            .singleElement().satisfies(e -> assertThat(e.endedEra()).isEqualTo(1));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {                 // R-10, after commit
            mvc.perform(get("/api/clubs/{id}/membership/me", clubId).with(user(applicant, false)))
                .andExpect(jsonPath("$.pendingRequest").doesNotExist());
            mvc.perform(get("/api/me/ownership-invitations").with(user(member, false)))
                .andExpect(jsonPath("$", empty()));
        });
    }

    @Test
    void everyWriteIsRefused() throws Exception {
        archive();

        send(post("/api/channels/{id}/posts", channelId), founder, "{\"body\":\"Still here?\"}")
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CHANNEL_WRITE_DENIED"));
        send(post("/api/posts/{id}/comments", postId), member, "{\"body\":\"Hello?\"}")
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CHANNEL_WRITE_DENIED"));
        send(post("/api/clubs/{id}/join", clubId), UUID.randomUUID(), "{}")
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("CLUB_NOT_ACTIVE"));
        send(post("/api/clubs/{id}/ownership-invitations", clubId), founder, "{\"inviteeId\":\"" + member + "\",\"transfer\":true}")
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("CLUB_NOT_ACTIVE"));
        send(post("/api/admin/clubs/{id}/archive", clubId), admin, "{\"reason\":\"Again\"}")
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("CLUB_NOT_ACTIVE"));
    }

    @Test
    void everythingStaysReadable() throws Exception {
        archive();
        UUID visitor = UUID.randomUUID();

        mvc.perform(get("/api/clubs/{slug}", slug).with(user(visitor, false)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ARCHIVED"));
        mvc.perform(get("/api/channels/{id}/posts", channelId).with(user(visitor, false)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].body", contains("Crane tutorial")))
            .andExpect(jsonPath("$.items[0].readOnly").value(true));
        mvc.perform(get("/api/posts/{id}/comments", postId).with(user(visitor, false)))
            .andExpect(jsonPath("$.items[*].body", contains("Thanks!")));
        mvc.perform(get("/api/clubs/{id}/spaces", clubId).with(user(member, false)))
            .andExpect(jsonPath("$[0].channels[0].canWrite").value(false));
    }

    @Test
    void theDirectoryHidesItUnlessAskedForArchivedClubs() throws Exception {
        archive();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
            mvc.perform(get("/api/public/clubs").param("q", name).param("status", "ALL"))
                .andExpect(jsonPath("$.items[*].status", contains("ARCHIVED"))));
        mvc.perform(get("/api/public/clubs").param("q", name))
            .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void onlyAdminsArchiveAndAReasonIsRequired() throws Exception {
        send(post("/api/admin/clubs/{id}/archive", clubId), founder, "{\"reason\":\"Mine\"}")
            .andExpect(status().isForbidden());
        send(post("/api/admin/clubs/{id}/archive", clubId), admin, "{\"reason\":\"  \"}")
            .andExpect(status().isBadRequest());
    }
}
