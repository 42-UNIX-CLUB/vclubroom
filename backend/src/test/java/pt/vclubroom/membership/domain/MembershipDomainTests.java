package pt.vclubroom.membership.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pt.vclubroom.membership.api.ClubPermission;
import pt.vclubroom.shared.error.BusinessRuleException;

class MembershipDomainTests {

    private static final Instant NOW = Instant.parse("2026-10-18T20:00:00Z");
    private final UUID club = UUID.randomUUID();

    private static void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.code()).isEqualTo(code));
    }

    @Test
    void theOwnerRoleAlwaysHasEveryPermissionAndCannotChange() {
        ClubRole owner = ClubRole.systemOwner(club, NOW);
        assertThat(owner.effectivePermissions()).isEqualTo(EnumSet.allOf(ClubPermission.class));
        assertCode(() -> owner.changePermissions(Set.of()), "SYSTEM_ROLE_IMMUTABLE");
        assertCode(owner::ensureDeletable, "SYSTEM_ROLE_IMMUTABLE");
    }

    @Test
    void membersHaveNoPermissionUntilACustomRoleGivesOne() {
        assertThat(ClubRole.systemMember(club, NOW).effectivePermissions()).isEmpty();
        ClubRole events = ClubRole.custom(club, "Event team", "#22c55e", Set.of(ClubPermission.EVENT_MANAGE), 10, NOW);
        assertThat(events.effectivePermissions()).containsExactly(ClubPermission.EVENT_MANAGE);
        events.rename("Events", "#16a34a");                       // custom roles can change
        assertThat(events.name()).isEqualTo("Events");
    }

    @Test
    void aMembershipStartsWithTheMemberRoleAndEndsOnlyOnce() {
        UUID memberRole = UUID.randomUUID();
        Membership m = Membership.start(club, UUID.randomUUID(), memberRole, NOW);
        assertThat(m.status()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(m.holds(memberRole)).isTrue();

        m.leave(NOW);
        assertThat(m.status()).isEqualTo(MembershipStatus.LEFT);
        assertCode(() -> m.grantRole(UUID.randomUUID()), "MEMBERSHIP_NOT_ACTIVE");
    }

    @Test
    void aJoinRequestIsDecidedOnce() {
        JoinRequest request = JoinRequest.submit(club, UUID.randomUUID(), "I play every week", NOW);
        request.approve(UUID.randomUUID(), NOW);
        assertThat(request.status()).isEqualTo(JoinRequestStatus.APPROVED);
        assertCode(() -> request.reject(UUID.randomUUID(), NOW), "JOIN_REQUEST_NOT_PENDING");
    }

    @Test
    void anInvitationExpiresAfterSevenDays() {
        OwnershipInvitation invitation = OwnershipInvitation.send(club, UUID.randomUUID(), UUID.randomUUID(), true, NOW);
        assertThat(invitation.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertCode(() -> invitation.accept(NOW.plus(Duration.ofDays(8))), "INVITATION_EXPIRED");

        OwnershipInvitation fresh = OwnershipInvitation.send(club, UUID.randomUUID(), UUID.randomUUID(), false, NOW);
        fresh.accept(NOW.plus(Duration.ofDays(1)));
        assertThat(fresh.status()).isEqualTo(InvitationStatus.ACCEPTED);
        assertCode(() -> fresh.decline(NOW), "INVITATION_NOT_PENDING");
    }

    @Test
    void aClubKeepsAtLeastOneOwner() {
        assertCode(() -> ClubOwnership.ensureAnotherOwnerRemains(1), "LAST_OWNER");
        ClubOwnership.ensureAnotherOwnerRemains(2);                  // a co-owner remains: fine
    }
}
