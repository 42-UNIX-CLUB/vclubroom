package pt.vclubroom.membership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pt.vclubroom.TestRequests.user;
import static pt.vclubroom.TestRequests.xsrf;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import pt.vclubroom.TestRequests;
import pt.vclubroom.TestcontainersConfiguration;
import pt.vclubroom.club.api.JoinPolicy;
import pt.vclubroom.club.application.ClubService;
import pt.vclubroom.club.application.CreateClubCommand;
import pt.vclubroom.club.persistence.ClubRepository;
import pt.vclubroom.membership.api.ClubAuthorization;
import pt.vclubroom.membership.application.OwnershipService;
import pt.vclubroom.membership.events.OwnerAdded;
import pt.vclubroom.membership.events.OwnerRemoved;
import pt.vclubroom.membership.events.OwnerSteppedDown;
import pt.vclubroom.membership.persistence.OwnershipInvitationRepository;
import pt.vclubroom.shared.error.BusinessRuleException;
import pt.vclubroom.shared.security.Actor;

/** Co-ownership and ownership transfer (E13-S01), over HTTP, in a real OPEN club. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class OwnershipIntegrationTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    ClubService clubs;

    @Autowired
    ClubRepository clubRepository;

    @Autowired
    ClubAuthorization authorization;

    @Autowired
    OwnershipService ownership;

    @Autowired
    OwnershipInvitationRepository invitations;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ApplicationEvents events;

    private final UUID founder = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();
    private UUID clubId;

    /** An approved OPEN club: its founder is the owner, and a second student has joined. */
    @BeforeEach
    void aClubWithAnOwnerAndAMember() throws Exception {
        clubId = clubs.requestCreation(new Actor(founder, false),
            new CreateClubCommand("Rowing " + TestRequests.suffix(), null, null, "Sports", List.of())).id();
        clubs.approve(new Actor(UUID.randomUUID(), true), clubId);
        await().atMost(Duration.ofSeconds(10)).until(() -> authorization.isOwner(clubId, founder));
        tx.executeWithoutResult(s -> clubRepository.findById(clubId).orElseThrow().changeJoinPolicy(JoinPolicy.OPEN, Instant.now()));
        mvc.perform(post("/api/clubs/{id}/join", clubId).with(user(member, false)).with(xsrf(mvc)))
            .andExpect(status().isCreated());
    }

    private ResultActions invite(UUID by, UUID invitee, boolean transfer) throws Exception {
        return mvc.perform(post("/api/clubs/{id}/ownership-invitations", clubId).with(user(by, false)).with(xsrf(mvc))
            .contentType(MediaType.APPLICATION_JSON).content("{\"inviteeId\":\"" + invitee + "\",\"transfer\":" + transfer + "}"));
    }

    private String invitationOf(UUID invitee, boolean transfer) throws Exception {
        String body = invite(founder, invitee, transfer).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.invitationId");
    }

    private ResultActions answer(UUID by, String invitationId, String action) throws Exception {
        return mvc.perform(post("/api/ownership-invitations/{id}/" + action, invitationId).with(user(by, false)).with(xsrf(mvc)));
    }

    private ResultActions stepDown(UUID by) throws Exception {
        return mvc.perform(post("/api/clubs/{id}/owners/me/step-down", clubId).with(user(by, false)).with(xsrf(mvc)));
    }

    @Test
    void aTransferMakesTheInviteeOwnerAndTheInviterStepDown() throws Exception {
        String invitation = invitationOf(member, true);
        mvc.perform(get("/api/me/ownership-invitations").with(user(member, false)))
            .andExpect(jsonPath("$[*].id", contains(invitation)))
            .andExpect(jsonPath("$[0].transfer").value(true))
            .andExpect(jsonPath("$[0].clubName").value(org.hamcrest.Matchers.startsWith("Rowing")));
        mvc.perform(get("/api/clubs/{id}/membership/me", clubId).with(user(member, false)))
            .andExpect(jsonPath("$.pendingInvitation.id").value(invitation));

        answer(member, invitation, "accept").andExpect(status().isNoContent());

        assertThat(authorization.isOwner(clubId, member)).isTrue();
        assertThat(authorization.isOwner(clubId, founder)).isFalse();             // same transaction
        assertThat(events.stream(OwnerAdded.class).filter(e -> e.clubId().equals(clubId))).hasSize(2);   // founder, member
        assertThat(events.stream(OwnerSteppedDown.class).filter(e -> e.clubId().equals(clubId)))
            .singleElement().satisfies(e -> assertThat(e.userId()).isEqualTo(founder));
        mvc.perform(get("/api/me/ownership-invitations").with(user(member, false))).andExpect(jsonPath("$", empty()));
    }

    @Test
    void aCoOwnerCanStepDownButTheLastOwnerCannot() throws Exception {
        answer(member, invitationOf(member, false), "accept").andExpect(status().isNoContent());
        assertThat(authorization.isOwner(clubId, founder)).isTrue();
        assertThat(authorization.isOwner(clubId, member)).isTrue();

        stepDown(founder).andExpect(status().isNoContent());
        stepDown(member)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("LAST_OWNER"));
        stepDown(founder)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("NOT_OWNER"));
    }

    @Test
    void twoOwnersSteppingDownAtTheSameMomentLeaveOneOwner() throws Exception {
        answer(member, invitationOf(member, false), "accept").andExpect(status().isNoContent());
        CyclicBarrier together = new CyclicBarrier(2);
        List<String> outcomes = Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        for (UUID owner : List.of(founder, member)) {
            pool.submit(() -> {
                try {
                    together.await();
                    ownership.stepDown(new Actor(owner, false), clubId);
                    outcomes.add("stepped down");
                } catch (BusinessRuleException e) {
                    outcomes.add(e.code());
                } catch (Exception e) {
                    outcomes.add(e.toString());
                }
            });
        }
        pool.shutdown();
        assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue();

        assertThat(outcomes).containsExactlyInAnyOrder("stepped down", "LAST_OWNER");
        assertThat(authorization.isOwner(clubId, founder) ^ authorization.isOwner(clubId, member)).isTrue();
    }

    @Test
    void invitationsFollowTheRules() throws Exception {
        invite(member, founder, false)
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_OWNER"));
        invite(founder, stranger, false)
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("INVITEE_NOT_MEMBER"));
        invite(founder, founder, false)
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_OWNER"));
        String invitation = invitationOf(member, false);
        invite(founder, member, true)
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVITATION_PENDING"));
        answer(stranger, invitation, "accept")                                        // someone else's: not found
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("INVITATION_NOT_FOUND"));

        mvc.perform(get("/api/clubs/{id}/ownership-invitations", clubId).with(user(founder, false)))
            .andExpect(jsonPath("$[*].id", contains(invitation)));
        mvc.perform(get("/api/clubs/{id}/ownership-invitations", clubId).with(user(member, false)))
            .andExpect(status().isForbidden());

        answer(member, invitation, "decline").andExpect(status().isNoContent());
        answer(member, invitation, "accept")
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("INVITATION_NOT_PENDING"));
        assertThat(authorization.isOwner(clubId, member)).isFalse();
    }

    @Test
    void anOwnerCanRevokeAPendingInvitation() throws Exception {
        String invitation = invitationOf(member, false);
        mvc.perform(delete("/api/ownership-invitations/{id}", invitation).with(user(member, false)).with(xsrf(mvc)))
            .andExpect(status().isForbidden());
        mvc.perform(delete("/api/ownership-invitations/{id}", invitation).with(user(founder, false)).with(xsrf(mvc)))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/me/ownership-invitations").with(user(member, false))).andExpect(jsonPath("$", empty()));
    }

    @Test
    void invitationsExpireAfterSevenDays() throws Exception {
        String invitation = invitationOf(member, false);
        jdbc.update("update membership.ownership_invitations set expires_at = now() - interval '1 minute' where id = ?::uuid", invitation);

        answer(member, invitation, "accept")
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("INVITATION_EXPIRED"));
        tx.executeWithoutResult(s -> invitations.expireAllBefore(Instant.now()));       // what the hourly job runs
        mvc.perform(get("/api/me/ownership-invitations").with(user(member, false))).andExpect(jsonPath("$", empty()));
    }

    @Test
    void adminsAssignAndRemoveOwners() throws Exception {
        UUID admin = UUID.randomUUID();
        mvc.perform(post("/api/admin/clubs/{id}/owners", clubId).with(user(admin, true)).with(xsrf(mvc))
                .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"" + stranger + "\"}"))
            .andExpect(status().isNoContent());
        assertThat(authorization.isOwner(clubId, stranger)).isTrue();                   // admitted, then made owner
        mvc.perform(get("/api/clubs/{id}/member-summary", clubId).with(user(founder, false)))
            .andExpect(jsonPath("$.memberCount").value(3));

        mvc.perform(delete("/api/admin/clubs/{id}/owners/{userId}", clubId, founder).with(user(admin, true)).with(xsrf(mvc)))
            .andExpect(status().isNoContent());
        assertThat(events.stream(OwnerRemoved.class).filter(e -> e.clubId().equals(clubId)))
            .singleElement().satisfies(e -> assertThat(e.reason()).isEqualTo("ADMIN"));
        mvc.perform(delete("/api/admin/clubs/{id}/owners/{userId}", clubId, stranger).with(user(admin, true)).with(xsrf(mvc)))
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("LAST_OWNER"));
        mvc.perform(post("/api/admin/clubs/{id}/owners", clubId).with(user(founder, false)).with(xsrf(mvc))
                .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"" + founder + "\"}"))
            .andExpect(status().isForbidden());
    }
}
