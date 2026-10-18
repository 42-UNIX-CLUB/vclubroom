package pt.vclubroom.membership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import pt.vclubroom.TestRequests;
import pt.vclubroom.TestcontainersConfiguration;
import pt.vclubroom.club.application.ClubDetails;
import pt.vclubroom.club.application.ClubService;
import pt.vclubroom.club.application.CreateClubCommand;
import pt.vclubroom.club.spi.ClubManagementGuard;
import pt.vclubroom.membership.api.ClubAuthorization;
import pt.vclubroom.membership.api.ClubPermission;
import pt.vclubroom.shared.error.BusinessRuleException;
import pt.vclubroom.shared.error.ForbiddenException;
import pt.vclubroom.shared.security.Actor;

/** The whole application: an admin approves a club in the club module, and membership makes the founder Owner. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FounderOwnershipIntegrationTests {

    private final Actor admin = new Actor(UUID.randomUUID(), true);
    private final Actor founder = new Actor(UUID.randomUUID(), false);
    private final Actor stranger = new Actor(UUID.randomUUID(), false);

    @Autowired
    ClubService clubs;

    @Autowired
    ClubAuthorization authorization;

    @Autowired
    ClubManagementGuard guard;

    private ClubDetails request() {
        return clubs.requestCreation(founder,
            new CreateClubCommand("Board Games " + TestRequests.suffix(), null, null, "Games", List.of()));
    }

    @Test
    void afterApprovalTheFounderOwnsTheClub() {
        UUID clubId = request().id();
        clubs.approve(admin, clubId);
        await().atMost(Duration.ofSeconds(10)).until(() -> authorization.isOwner(clubId, founder.userId()));  // async listener

        assertThat(guard.canManageSettings(clubId, founder)).isTrue();          // the club module's SPI, now membership's
        assertThat(guard.canManageSettings(clubId, stranger)).isFalse();
        assertThat(guard.canManageSettings(clubId, admin)).isTrue();
        assertThat(authorization.holdersOf(clubId, ClubPermission.MEMBER_APPROVE)).containsExactly(founder.userId());
        assertThat(authorization.clubIdsOf(founder.userId())).contains(clubId);
        assertThatThrownBy(() -> authorization.require(clubId, stranger, ClubPermission.MEMBER_REMOVE))
            .isInstanceOfSatisfying(ForbiddenException.class, e -> assertThat(e.code()).isEqualTo("PERMISSION_DENIED"));
    }

    @Test
    void aClubWaitingForApprovalHasNoMembersAndIsReadOnly() {
        UUID clubId = request().id();

        assertThat(authorization.activeMemberIds(clubId)).isEmpty();
        assertThatThrownBy(() -> authorization.require(clubId, admin, ClubPermission.CLUB_SETTINGS_MANAGE))
            .isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.code()).isEqualTo("CLUB_NOT_ACTIVE"));
    }
}
