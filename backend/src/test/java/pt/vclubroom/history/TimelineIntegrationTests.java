package pt.vclubroom.history;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
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

/** A club's whole life, as GET /api/clubs/{id}/timeline and /eras tell it (E14-S03). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TimelineIntegrationTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    ClubService clubs;

    @Autowired
    ClubRepository clubRepository;

    @Autowired
    ClubAuthorization authorization;

    @Autowired
    UserRepository users;

    @Autowired
    TransactionTemplate tx;

    private final UUID admin = UUID.randomUUID();
    private final UUID visitor = UUID.randomUUID();

    private User person(String name) {
        String login = name.toLowerCase().split(" ")[0] + "-" + TestRequests.suffix();
        return users.save(User.createLocal(login, name, login + "@example.test", "{noop}x", PlatformRole.USER, Instant.now()));
    }

    @Test
    void theTimelineTellsTheClubsLifeNewestFirstWithNames() throws Exception {
        UUID founder = person("Ada Founder").id();
        UUID member = person("Bob Member").id();
        UUID clubId = clubs.requestCreation(new Actor(founder, false),
            new CreateClubCommand("Chess " + TestRequests.suffix(), null, null, "Games", List.of())).id();
        mvc.perform(get("/api/clubs/{id}/timeline", clubId).with(user(visitor, false)))
            .andExpect(status().isNotFound());                                       // not open yet: admins only
        clubs.approve(new Actor(admin, true), clubId);
        await().atMost(Duration.ofSeconds(10)).until(() -> authorization.isOwner(clubId, founder));
        tx.executeWithoutResult(s -> clubRepository.findById(clubId).orElseThrow().changeJoinPolicy(JoinPolicy.OPEN, Instant.now()));
        mvc.perform(post("/api/clubs/{id}/join", clubId).with(user(member, false)).with(xsrf(mvc))).andExpect(status().isCreated());
        String invitation = JsonPath.read(mvc.perform(post("/api/clubs/{id}/ownership-invitations", clubId)
                .with(user(founder, false)).with(xsrf(mvc)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"inviteeId\":\"" + member + "\",\"transfer\":false}"))
            .andReturn().getResponse().getContentAsString(), "$.invitationId");
        mvc.perform(post("/api/ownership-invitations/{id}/accept", invitation).with(user(member, false)).with(xsrf(mvc)))
            .andExpect(status().isNoContent());
        mvc.perform(post("/api/clubs/{id}/owners/me/step-down", clubId).with(user(founder, false)).with(xsrf(mvc)))
            .andExpect(status().isNoContent());
        clubs.archive(new Actor(admin, true), clubId, "Everyone graduated");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
            mvc.perform(get("/api/clubs/{id}/timeline", clubId).with(user(visitor, false)))
                .andExpect(jsonPath("$.items", hasSize(6))));
        mvc.perform(get("/api/clubs/{id}/timeline", clubId).with(user(visitor, false)))       // archived: still readable
            .andExpect(jsonPath("$.items[*].type", contains(
                "ARCHIVED", "OWNER_STEPPED_DOWN", "OWNER_ADDED", "OWNER_ADDED", "FOUNDED", "CREATION_REQUESTED")))
            .andExpect(jsonPath("$.items[*].era", contains(1, 1, 1, 1, 1, 0)))
            .andExpect(jsonPath("$.items[0].data.reason").value("Everyone graduated"))
            .andExpect(jsonPath("$.items[0].subject").value(nullValue()))
            .andExpect(jsonPath("$.items[1].subject.displayName").value("Ada Founder"))
            .andExpect(jsonPath("$.items[2].subject.displayName").value("Bob Member"))
            .andExpect(jsonPath("$.items[2].actor.displayName").value("Ada Founder"))
            .andExpect(jsonPath("$.items[2].data.reason").value("INVITATION"))
            .andExpect(jsonPath("$.items[4].subject.displayName").value("Ada Founder"));

        String first = mvc.perform(get("/api/clubs/{id}/timeline", clubId).param("limit", "4").with(user(visitor, false)))
            .andExpect(jsonPath("$.nextCursor").value(notNullValue()))
            .andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/clubs/{id}/timeline", clubId).param("limit", "4")
                .param("cursor", JsonPath.<String>read(first, "$.nextCursor")).with(user(visitor, false)))
            .andExpect(jsonPath("$.items[*].type", contains("FOUNDED", "CREATION_REQUESTED")))
            .andExpect(jsonPath("$.nextCursor").value(nullValue()));

        mvc.perform(get("/api/clubs/{id}/eras", clubId).with(user(visitor, false)))
            .andExpect(jsonPath("$[*].number", contains(1)))
            .andExpect(jsonPath("$[0].endedAt").value(notNullValue()))
            .andExpect(jsonPath("$[0].endReason").value("Everyone graduated"));
    }
}
