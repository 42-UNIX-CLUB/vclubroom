package pt.vclubroom.notification;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import pt.vclubroom.TestRequests;
import pt.vclubroom.TestcontainersConfiguration;
import pt.vclubroom.club.api.JoinPolicy;
import pt.vclubroom.club.application.ClubService;
import pt.vclubroom.club.application.CreateClubCommand;
import pt.vclubroom.club.persistence.ClubRepository;
import pt.vclubroom.identity.api.PlatformRole;
import pt.vclubroom.identity.domain.User;
import pt.vclubroom.identity.persistence.UserRepository;
import pt.vclubroom.membership.api.ClubAuthorization;
import pt.vclubroom.shared.security.Actor;
import pt.vclubroom.space.api.SpaceAccessPolicy;

/** Notifications through the real flows, and the inbox over HTTP (E12-S01). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class NotificationIntegrationTests {

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
    UserRepository users;

    @Autowired
    TransactionTemplate tx;

    private User person(String name, PlatformRole role) {
        String login = name.toLowerCase() + "-" + TestRequests.suffix();
        return users.save(User.createLocal(login, name, login + "@example.test", "{noop}x", role, Instant.now()));
    }

    private String inbox(UUID who) throws Exception {
        return mvc.perform(get("/api/notifications").with(user(who, false))).andReturn().getResponse().getContentAsString();
    }

    @Test
    void theClubLifecycleAndTheForumNotifyTheRightPeople() throws Exception {
        UUID admin = person("Alice", PlatformRole.ADMIN).id();
        UUID founder = person("Bob", PlatformRole.USER).id();
        UUID member = person("Carol", PlatformRole.USER).id();
        var club = clubs.requestCreation(new Actor(founder, false), new CreateClubCommand("Chess " + TestRequests.suffix(), null, null, "Games", List.of()));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> mvc.perform(get("/api/notifications").with(user(admin, true)))
            .andExpect(jsonPath("$.items[*].type", hasItem("CLUB_CREATION_SUBMITTED"))));

        clubs.approve(new Actor(admin, true), club.id());
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> mvc.perform(get("/api/notifications").with(user(founder, false)))
            .andExpect(jsonPath("$.items[0].type").value("CLUB_APPROVED"))
            .andExpect(jsonPath("$.items[0].link").value("/c/" + club.slug())));

        await().atMost(Duration.ofSeconds(10)).until(() -> authorization.isOwner(club.id(), founder)
            && !spaceAccess.readableForumChannels(club.id(), new Actor(founder, false)).isEmpty());
        tx.executeWithoutResult(s -> clubRepository.findById(club.id()).orElseThrow().changeJoinPolicy(JoinPolicy.OPEN, Instant.now()));
        mvc.perform(post("/api/clubs/{id}/join", club.id()).with(user(member, false)).with(xsrf(mvc))).andExpect(status().isCreated());
        UUID channel = spaceAccess.readableForumChannels(club.id(), new Actor(founder, false)).iterator().next();
        String postId = JsonPath.read(mvc.perform(post("/api/channels/{id}/posts", channel).with(user(founder, false)).with(xsrf(mvc))
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Friday blitz\",\"body\":\"Bring a clock\"}"))
            .andReturn().getResponse().getContentAsString(), "$.id");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> mvc.perform(get("/api/notifications").with(user(member, false)))
            .andExpect(jsonPath("$.items[0].type").value("NEW_POST"))
            .andExpect(jsonPath("$.items[0].title").value("Friday blitz"))
            .andExpect(jsonPath("$.items[0].link").value("/posts/" + postId)));
        mvc.perform(post("/api/posts/{id}/comments", postId).with(user(member, false)).with(xsrf(mvc))
            .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"See you there\"}")).andExpect(status().isCreated());
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> mvc.perform(get("/api/notifications").with(user(founder, false)))
            .andExpect(jsonPath("$.items[0].type").value("NEW_COMMENT")));
        mvc.perform(get("/api/notifications").with(user(founder, false)))
            .andExpect(jsonPath("$.items[*].type", contains("NEW_COMMENT", "CLUB_APPROVED")));        // not about my own post

        mvc.perform(get("/api/posts/{id}", postId).with(user(member, false)))
            .andExpect(jsonPath("$.channelId").value(channel.toString()));
        mvc.perform(get("/api/posts/{id}", UUID.randomUUID()).with(user(member, false)))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    }

    @Test
    void theInboxCountsAndMarksOnlyMine() throws Exception {
        UUID founder = person("Dave", PlatformRole.USER).id();
        var club = clubs.requestCreation(new Actor(founder, false), new CreateClubCommand("Go " + TestRequests.suffix(), null, null, "Games", List.of()));
        clubs.reject(new Actor(UUID.randomUUID(), true), club.id(), "Same as the Go Society");
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> mvc.perform(get("/api/notifications/unread-count").with(user(founder, false)))
            .andExpect(jsonPath("$.count").value(1)));
        String id = JsonPath.read(inbox(founder), "$.items[0].id");

        mvc.perform(post("/api/notifications/{id}/read", id).with(user(UUID.randomUUID(), false)).with(xsrf(mvc)))
            .andExpect(status().isNoContent());                                        // someone else's: nothing happens
        mvc.perform(get("/api/notifications/unread-count").with(user(founder, false))).andExpect(jsonPath("$.count").value(1));
        mvc.perform(post("/api/notifications/{id}/read", id).with(user(founder, false)).with(xsrf(mvc))).andExpect(status().isNoContent());
        mvc.perform(get("/api/notifications").param("unreadOnly", "true").with(user(founder, false)))
            .andExpect(jsonPath("$.items", empty()));
        mvc.perform(get("/api/notifications").with(user(founder, false)))
            .andExpect(jsonPath("$.items[0].read").value(true))
            .andExpect(jsonPath("$.items[0].body").value("Same as the Go Society"));
        mvc.perform(post("/api/notifications/read-all").with(user(founder, false)).with(xsrf(mvc))).andExpect(status().isNoContent());
        mvc.perform(get("/api/notifications/unread-count").with(user(founder, false))).andExpect(jsonPath("$.count").value(0));
    }
}
