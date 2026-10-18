package pt.vclubroom.club;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.Scenario;
import pt.vclubroom.TestRequests;
import pt.vclubroom.TestcontainersConfiguration;
import pt.vclubroom.club.api.ClubStatus;
import pt.vclubroom.club.application.ClubDetails;
import pt.vclubroom.club.application.ClubService;
import pt.vclubroom.club.application.CreateClubCommand;
import pt.vclubroom.club.events.ClubApproved;
import pt.vclubroom.club.events.ClubProfileUpdated;
import pt.vclubroom.club.events.ClubRejected;
import pt.vclubroom.shared.security.Actor;

/** The club module started on its own: what it announces when an admin decides (E04-S02-T3). */
@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
class ClubApprovalModuleTests {

    private final Actor admin = new Actor(UUID.randomUUID(), true);

    @Autowired
    ClubService clubs;

    private ClubDetails pendingClub(UUID founder) {
        return clubs.requestCreation(new Actor(founder, false),
            new CreateClubCommand("Robotics " + TestRequests.suffix(), "Build things", null, "Tech", List.of("robots")));
    }

    @Test
    void approvingAClubStartsEraOneAndAnnouncesIt(Scenario scenario) {
        UUID founder = UUID.randomUUID();
        ClubDetails pending = pendingClub(founder);

        scenario.stimulate(() -> clubs.approve(admin, pending.id()))
            .andWaitForEventOfType(ClubApproved.class)
            .matching(event -> event.clubId().equals(pending.id()))
            .toArriveAndVerify(event -> {
                assertThat(event.era()).isEqualTo(1);
                assertThat(event.founderId()).isEqualTo(founder);
                assertThat(event.approvedBy()).isEqualTo(admin.userId());
            });
    }

    @Test
    void approvalAlsoPublishesTheFullPublicProfile(Scenario scenario) {
        ClubDetails pending = pendingClub(UUID.randomUUID());

        scenario.stimulate(() -> clubs.approve(admin, pending.id()))
            .andWaitForEventOfType(ClubProfileUpdated.class)
            .matching(event -> event.clubId().equals(pending.id()))
            .toArriveAndVerify(event -> {
                assertThat(event.status()).isEqualTo(ClubStatus.ACTIVE);
                assertThat(event.slug()).isEqualTo(pending.slug());
                assertThat(event.tags()).containsExactly("robots");
            });
    }

    @Test
    void rejectionAnnouncesTheReason(Scenario scenario) {
        ClubDetails pending = pendingClub(UUID.randomUUID());

        scenario.stimulate(() -> clubs.reject(admin, pending.id(), "Duplicate of the Makers club"))
            .andWaitForEventOfType(ClubRejected.class)
            .matching(event -> event.clubId().equals(pending.id()))
            .toArriveAndVerify(event -> assertThat(event.reason()).isEqualTo("Duplicate of the Makers club"));
    }
}
