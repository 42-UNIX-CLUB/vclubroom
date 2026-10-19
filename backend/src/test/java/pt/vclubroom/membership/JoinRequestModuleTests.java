package pt.vclubroom.membership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.Scenario;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pt.vclubroom.TestcontainersConfiguration;
import pt.vclubroom.club.api.ClubCatalog;
import pt.vclubroom.club.api.ClubSnapshot;
import pt.vclubroom.club.api.ClubStatus;
import pt.vclubroom.club.api.JoinPolicy;
import pt.vclubroom.club.events.ClubApproved;
import pt.vclubroom.club.events.ClubProfileUpdated;
import pt.vclubroom.identity.api.UserDirectory;
import pt.vclubroom.membership.application.JoinService;
import pt.vclubroom.membership.events.JoinRequestDecided;
import pt.vclubroom.membership.events.OwnerAdded;
import pt.vclubroom.membership.persistence.JoinRequestRepository;
import pt.vclubroom.membership.persistence.MembershipRepository;
import pt.vclubroom.shared.security.Actor;

/**
 * Deciding join requests, with the membership module started on its own (E07-S03). Same mocks as
 * ClubLifecycleModuleTests, so both classes share one Spring context.
 */
@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
class JoinRequestModuleTests {

    @MockitoBean
    ClubCatalog clubCatalog;

    @MockitoBean
    UserDirectory userDirectory;

    @Autowired
    JoinService joins;

    @Autowired
    MembershipRepository memberships;

    @Autowired
    JoinRequestRepository joinRequests;

    /** An approved APPROVAL club whose founder is already Owner (the listener of E07-S01 ran). */
    private UUID approvedClub(Scenario scenario, UUID founder) {
        UUID clubId = UUID.randomUUID();
        given(clubCatalog.require(clubId)).willReturn(new ClubSnapshot(clubId, "chess", "Chess Club", ClubStatus.ACTIVE, JoinPolicy.APPROVAL, 1));
        scenario.publish(new ClubApproved(clubId, "chess", "Chess Club", founder, UUID.randomUUID(), 1, Instant.now()))
            .andWaitForEventOfType(OwnerAdded.class).matching(e -> e.clubId().equals(clubId)).toArrive();
        return clubId;
    }

    @Test
    void approvingAdmitsTheRequesterAndAnnouncesIt(Scenario scenario) {
        Actor founder = new Actor(UUID.randomUUID(), false);
        UUID student = UUID.randomUUID();
        UUID clubId = approvedClub(scenario, founder.userId());
        UUID requestId = joins.join(new Actor(student, false), clubId, "Hello").requestId();

        scenario.stimulate(() -> joins.approve(founder, clubId, requestId))
            .andWaitForEventOfType(JoinRequestDecided.class)
            .matching(e -> e.requestId().equals(requestId))
            .toArriveAndVerify(e -> {
                assertThat(e.approved()).isTrue();
                assertThat(e.decidedBy()).isEqualTo(founder.userId());
                assertThat(memberships.existsActive(clubId, student)).isTrue();
            });
    }

    @Test
    void rejectingClosesTheRequestAndAnnouncesIt(Scenario scenario) {
        Actor founder = new Actor(UUID.randomUUID(), false);
        UUID student = UUID.randomUUID();
        UUID clubId = approvedClub(scenario, founder.userId());
        UUID requestId = joins.join(new Actor(student, false), clubId, null).requestId();

        scenario.stimulate(() -> joins.reject(founder, clubId, requestId))
            .andWaitForEventOfType(JoinRequestDecided.class)
            .matching(e -> e.requestId().equals(requestId))
            .toArriveAndVerify(e -> {
                assertThat(e.approved()).isFalse();
                assertThat(memberships.existsActive(clubId, student)).isFalse();
                assertThat(joinRequests.existsPending(clubId, student)).isFalse();
            });
    }

    @Test
    void switchingTheClubToOpenApprovesEveryPendingRequest(Scenario scenario) {
        UUID founder = UUID.randomUUID();
        UUID clubId = approvedClub(scenario, founder);
        List<UUID> students = List.of(UUID.randomUUID(), UUID.randomUUID());
        students.forEach(student -> joins.join(new Actor(student, false), clubId, null));

        scenario.publish(new ClubProfileUpdated(clubId, "chess", "Chess Club", null, null, "Games", List.of(), null, null,
                JoinPolicy.OPEN, ClubStatus.ACTIVE, 1, Instant.now()))
            .andWaitForStateChange(() -> memberships.findActiveUserIds(clubId), ids -> ids.size() == 3)
            .andVerify(ids -> {
                assertThat(ids).containsAll(students).contains(founder);
                assertThat(joinRequests.findAllPending(clubId)).isEmpty();
            });
    }
}
