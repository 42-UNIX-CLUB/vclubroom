package pt.vclubroom.membership.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.vclubroom.club.api.ClubCatalog;
import pt.vclubroom.club.api.ClubSnapshot;
import pt.vclubroom.club.api.ClubStatus;
import pt.vclubroom.club.api.JoinPolicy;
import pt.vclubroom.membership.api.ClubPermission;
import pt.vclubroom.membership.domain.ClubRole;
import pt.vclubroom.membership.persistence.ClubRoleRepository;
import pt.vclubroom.membership.persistence.MembershipRepository;
import pt.vclubroom.shared.error.BusinessRuleException;
import pt.vclubroom.shared.error.ForbiddenException;
import pt.vclubroom.shared.security.Actor;

/** The permission rules alone: repositories and the club catalogue are mocked. */
class ClubAuthorizationServiceTests {

    private static final Instant NOW = Instant.parse("2026-10-18T20:00:00Z");

    private final MembershipRepository memberships = mock(MembershipRepository.class);
    private final ClubRoleRepository roles = mock(ClubRoleRepository.class);
    private final ClubCatalog clubs = mock(ClubCatalog.class);
    private final ClubAuthorizationService authorization = new ClubAuthorizationService(memberships, roles, clubs);

    private final UUID club = UUID.randomUUID();
    private final Actor owner = new Actor(UUID.randomUUID(), false);
    private final Actor organiser = new Actor(UUID.randomUUID(), false);
    private final Actor stranger = new Actor(UUID.randomUUID(), false);
    private final Actor admin = new Actor(UUID.randomUUID(), true);

    @BeforeEach
    void roles() {
        ClubRole member = ClubRole.systemMember(club, NOW);
        given(roles.findHeldRoles(club, owner.userId())).willReturn(List.of(ClubRole.systemOwner(club, NOW), member));
        given(roles.findHeldRoles(club, organiser.userId())).willReturn(List.of(member,
            ClubRole.custom(club, "Events", null, Set.of(ClubPermission.EVENT_MANAGE), 10, NOW)));
        given(roles.findHeldRoles(club, stranger.userId())).willReturn(List.of());
        given(clubs.require(club)).willReturn(snapshot(ClubStatus.ACTIVE));
    }

    private ClubSnapshot snapshot(ClubStatus status) {
        return new ClubSnapshot(club, "chess", "Chess Club", status, JoinPolicy.OPEN, 1);
    }

    @Test
    void ownersHoldEveryPermissionOthersOnlyWhatTheirRolesGive() {
        assertThat(authorization.permissionsOf(club, owner)).containsExactlyInAnyOrder(ClubPermission.values());
        assertThat(authorization.permissionsOf(club, organiser)).containsExactly(ClubPermission.EVENT_MANAGE);
        assertThat(authorization.permissionsOf(club, stranger)).isEmpty();
        assertThat(authorization.isOwner(club, owner.userId())).isTrue();
        assertThat(authorization.isOwner(club, organiser.userId())).isFalse();
    }

    @Test
    void requireRefusesWithoutThePermission() {
        authorization.require(club, organiser, ClubPermission.EVENT_MANAGE);         // passes
        assertThatThrownBy(() -> authorization.require(club, organiser, ClubPermission.MEMBER_REMOVE))
            .isInstanceOfSatisfying(ForbiddenException.class, e -> assertThat(e.code()).isEqualTo("PERMISSION_DENIED"));
    }

    @Test
    void adminsPassWithoutBeingMembers() {
        authorization.require(club, admin, ClubPermission.MEMBER_REMOVE);            // R-08
        assertThat(authorization.canManageSettings(club, admin)).isTrue();
    }

    @Test
    void clubsThatAreNotActiveAreReadOnlyForEveryone() {
        given(clubs.require(club)).willReturn(snapshot(ClubStatus.ARCHIVED));
        assertThatThrownBy(() -> authorization.require(club, admin, ClubPermission.EVENT_MANAGE))
            .isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.code()).isEqualTo("CLUB_NOT_ACTIVE"));
    }

    @Test
    void theClubModuleAsksForTheSettingsPermission() {
        assertThat(authorization.canManageSettings(club, owner)).isTrue();
        assertThat(authorization.canManageSettings(club, organiser)).isFalse();
        assertThat(authorization.canManageSettings(club, stranger)).isFalse();
    }
}
