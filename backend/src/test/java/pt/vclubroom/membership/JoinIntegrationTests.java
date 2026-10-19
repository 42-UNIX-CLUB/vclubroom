package pt.vclubroom.membership;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pt.vclubroom.TestRequests.user;
import static pt.vclubroom.TestRequests.xsrf;

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
import pt.vclubroom.membership.api.ClubAuthorization;
import pt.vclubroom.shared.security.Actor;

/** Join, look at my membership, leave (E07-S02), over HTTP. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class JoinIntegrationTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    ClubService clubs;

    @Autowired
    ClubAuthorization authorization;

    @Autowired
    ClubRepository clubRepository;

    @Autowired
    TransactionTemplate tx;

    private final UUID founder = UUID.randomUUID();
    private final UUID student = UUID.randomUUID();

    /** An approved club whose founder already is its owner; OPEN or APPROVAL (the default). */
    private UUID activeClub(JoinPolicy policy) {
        UUID clubId = clubs.requestCreation(new Actor(founder, false),
            new CreateClubCommand("Hiking " + TestRequests.suffix(), null, null, "Sports", List.of())).id();
        clubs.approve(new Actor(UUID.randomUUID(), true), clubId);
        await().atMost(Duration.ofSeconds(10)).until(() -> authorization.isOwner(clubId, founder));
        if (policy == JoinPolicy.OPEN) {                 // no API for it until E04-S04 (club settings)
            tx.executeWithoutResult(status -> clubRepository.findById(clubId).orElseThrow().changeJoinPolicy(policy, Instant.now()));
        }
        return clubId;
    }

    @Test
    void anOpenClubAdmitsAtOnceAndOnlyOnce() throws Exception {
        UUID clubId = activeClub(JoinPolicy.OPEN);

        mvc.perform(post("/api/clubs/{id}/join", clubId).with(user(student, false)).with(xsrf(mvc)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("JOINED"))
            .andExpect(jsonPath("$.membershipId").value(notNullValue()));
        mvc.perform(get("/api/clubs/{id}/membership/me", clubId).with(user(student, false)))
            .andExpect(jsonPath("$.member").value(true))
            .andExpect(jsonPath("$.owner").value(false))
            .andExpect(jsonPath("$.roles[0].name").value("Member"))
            .andExpect(jsonPath("$.permissions", empty()));
        mvc.perform(get("/api/me/memberships").with(user(student, false)))
            .andExpect(jsonPath("$[*].clubId", hasItem(clubId.toString())));

        mvc.perform(post("/api/clubs/{id}/join", clubId).with(user(student, false)).with(xsrf(mvc)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ALREADY_MEMBER"));
    }

    @Test
    void anApprovalClubTakesARequestOnlyOnce() throws Exception {
        UUID clubId = activeClub(JoinPolicy.APPROVAL);

        mvc.perform(post("/api/clubs/{id}/join", clubId).with(user(student, false)).with(xsrf(mvc))
                .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"I hike every weekend\"}"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status").value("REQUESTED"));
        mvc.perform(get("/api/clubs/{id}/membership/me", clubId).with(user(student, false)))
            .andExpect(jsonPath("$.member").value(false))
            .andExpect(jsonPath("$.pendingRequest.id").value(notNullValue()))
            .andExpect(jsonPath("$.pendingInvitation").value(nullValue()));

        mvc.perform(post("/api/clubs/{id}/join", clubId).with(user(student, false)).with(xsrf(mvc)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("JOIN_ALREADY_REQUESTED"));
    }

    @Test
    void aClubThatIsNotActiveTakesNoMembers() throws Exception {
        UUID pending = clubs.requestCreation(new Actor(founder, false),
            new CreateClubCommand("Rowing " + TestRequests.suffix(), null, null, "Sports", List.of())).id();

        mvc.perform(post("/api/clubs/{id}/join", pending).with(user(student, false)).with(xsrf(mvc)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("CLUB_NOT_ACTIVE"));
    }

    @Test
    void membersLeaveButTheLastOwnerStays() throws Exception {
        UUID clubId = activeClub(JoinPolicy.OPEN);
        mvc.perform(post("/api/clubs/{id}/join", clubId).with(user(student, false)).with(xsrf(mvc)));

        mvc.perform(delete("/api/clubs/{id}/members/me", clubId).with(user(student, false)).with(xsrf(mvc)))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/clubs/{id}/membership/me", clubId).with(user(student, false)))
            .andExpect(jsonPath("$.member").value(false));
        mvc.perform(delete("/api/clubs/{id}/members/me", clubId).with(user(student, false)).with(xsrf(mvc)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("NOT_A_MEMBER"));

        mvc.perform(delete("/api/clubs/{id}/members/me", clubId).with(user(founder, false)).with(xsrf(mvc)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("LAST_OWNER"));
    }

    @Test
    void adminsHaveEveryPermissionWithoutBeingMembers() throws Exception {
        UUID clubId = activeClub(JoinPolicy.APPROVAL);

        mvc.perform(get("/api/clubs/{id}/membership/me", clubId).with(user(UUID.randomUUID(), true)))
            .andExpect(jsonPath("$.member").value(false))
            .andExpect(jsonPath("$.permissions", hasItem("CLUB_SETTINGS_MANAGE")));
    }
}
