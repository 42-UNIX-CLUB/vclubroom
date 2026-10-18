package pt.vclubroom.membership;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.Scenario;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pt.vclubroom.TestcontainersConfiguration;
import pt.vclubroom.club.api.ClubCatalog;
import pt.vclubroom.club.events.ClubApproved;
import pt.vclubroom.identity.api.UserDirectory;
import pt.vclubroom.membership.api.ClubAuthorization;
import pt.vclubroom.membership.domain.ClubRole;
import pt.vclubroom.membership.domain.SystemRole;
import pt.vclubroom.membership.events.MemberJoined;
import pt.vclubroom.membership.events.OwnerAdded;
import pt.vclubroom.membership.persistence.ClubRoleRepository;
import pt.vclubroom.membership.persistence.MembershipRepository;

/** The membership module started on its own (the club module is not): its reaction to ClubApproved (E07-S01-T6). */
@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
class ClubLifecycleModuleTests {

    @MockitoBean
    ClubCatalog clubCatalog;                   // from module "club": mocked, because the club module is not started

    @MockitoBean
    UserDirectory userDirectory;               // from module "identity", same reason

    @Autowired
    ClubAuthorization authorization;

    @Autowired
    MembershipRepository memberships;

    @Autowired
    ClubRoleRepository roles;

    @Autowired
    CompletedEventPublications completed;

    private static ClubApproved approved(UUID clubId, UUID founderId) {
        return new ClubApproved(clubId, "chess-" + clubId, "Chess Club", founderId, UUID.randomUUID(), 1, Instant.now());
    }

    @Test
    void theFounderBecomesOwnerWhenTheClubIsApproved(Scenario scenario) {
        UUID clubId = UUID.randomUUID();
        UUID founderId = UUID.randomUUID();

        scenario.publish(approved(clubId, founderId))
            .andWaitForEventOfType(OwnerAdded.class)
            .matching(event -> event.clubId().equals(clubId))
            .toArriveAndVerify(event -> {
                assertThat(event.userId()).isEqualTo(founderId);
                assertThat(event.reason()).isEqualTo("FOUNDER");
                assertThat(roles.findByClubIdOrderByPositionAsc(clubId)).extracting(ClubRole::systemRole)
                    .containsExactly(SystemRole.OWNER, SystemRole.MEMBER);
                assertThat(authorization.isOwner(clubId, founderId)).isTrue();
                assertThat(authorization.activeMemberIds(clubId)).containsExactly(founderId);
            });
    }

    @Test
    void theFounderJoinsWithTheMemberRoleToo(Scenario scenario) {
        UUID clubId = UUID.randomUUID();

        scenario.publish(approved(clubId, UUID.randomUUID()))
            .andWaitForEventOfType(MemberJoined.class)
            .matching(event -> event.clubId().equals(clubId))
            .toArriveAndVerify(event -> assertThat(event.via()).isEqualTo("FOUNDED"));
    }

    @Test
    void processingTheSameEventTwiceChangesNothing(Scenario scenario) {
        UUID clubId = UUID.randomUUID();
        UUID founderId = UUID.randomUUID();
        ClubApproved event = approved(clubId, founderId);

        scenario.publish(event).andWaitForEventOfType(OwnerAdded.class)
            .matching(added -> added.clubId().equals(clubId)).toArrive();

        // At-least-once delivery: the same event again. Nothing new is published, so wait until it was handled.
        scenario.publish(event)
            .andWaitForStateChange(() -> handled(clubId), times -> times == 2)
            .andVerify(times -> {
                assertThat(roles.findByClubIdOrderByPositionAsc(clubId)).hasSize(2);
                assertThat(memberships.findActiveUserIds(clubId)).containsExactly(founderId);
                assertThat(roles.findHeldRoles(clubId, founderId)).hasSize(2);
            });
    }

    /** How many deliveries of ClubApproved for this club the listener finished (the event publication registry). */
    private long handled(UUID clubId) {
        return completed.findAll().stream()
            .filter(publication -> publication.getEvent() instanceof ClubApproved e && e.clubId().equals(clubId))
            .count();
    }
}
