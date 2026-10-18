package pt.vclubroom.membership.application;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.club.api.ClubCatalog;
import pt.vclubroom.club.api.ClubSnapshot;
import pt.vclubroom.club.spi.ClubManagementGuard;
import pt.vclubroom.membership.api.ClubAuthorization;
import pt.vclubroom.membership.api.ClubPermission;
import pt.vclubroom.membership.domain.ClubRole;
import pt.vclubroom.membership.persistence.ClubRoleRepository;
import pt.vclubroom.membership.persistence.MembershipRepository;
import pt.vclubroom.shared.error.BusinessRuleException;
import pt.vclubroom.shared.error.ForbiddenException;
import pt.vclubroom.shared.security.Actor;

/** Implements the public API (ClubAuthorization) and the club module's SPI (ClubManagementGuard). */
@Service
@Transactional(readOnly = true)
class ClubAuthorizationService implements ClubAuthorization, ClubManagementGuard {

    private final MembershipRepository memberships;
    private final ClubRoleRepository roles;
    private final ClubCatalog clubs;

    ClubAuthorizationService(MembershipRepository memberships, ClubRoleRepository roles, ClubCatalog clubs) {
        this.memberships = memberships;
        this.roles = roles;
        this.clubs = clubs;
    }

    @Override
    public boolean isActiveMember(UUID clubId, UUID userId) {
        return memberships.existsActive(clubId, userId);
    }

    @Override
    public boolean isOwner(UUID clubId, UUID userId) {
        return roles.findHeldRoles(clubId, userId).stream().anyMatch(ClubRole::isOwner);
    }

    @Override
    public Set<ClubPermission> permissionsOf(UUID clubId, Actor actor) {
        if (actor.admin()) {
            return EnumSet.allOf(ClubPermission.class);                 // R-08 break-glass
        }
        List<ClubRole> held = roles.findHeldRoles(clubId, actor.userId());
        EnumSet<ClubPermission> result = EnumSet.noneOf(ClubPermission.class);
        held.forEach(role -> result.addAll(role.effectivePermissions()));
        return result;
    }

    @Override
    public boolean hasPermission(UUID clubId, Actor actor, ClubPermission permission) {
        return permissionsOf(clubId, actor).contains(permission);
    }

    @Override
    public void require(UUID clubId, Actor actor, ClubPermission permission) {
        ClubSnapshot club = clubs.require(clubId);
        if (!club.isActive()) {
            throw new BusinessRuleException("CLUB_NOT_ACTIVE", "This club is " + club.status() + " and is read-only.");
        }
        if (!hasPermission(clubId, actor, permission)) {
            throw new ForbiddenException("PERMISSION_DENIED", "Missing club permission " + permission + ".");
        }
    }

    @Override
    public Set<UUID> activeMemberIds(UUID clubId) {
        return memberships.findActiveUserIds(clubId);
    }

    @Override
    public Set<UUID> holdersOf(UUID clubId, ClubPermission permission) {
        return roles.findHolderIds(clubId, permission.name());
    }

    @Override
    public Set<UUID> clubIdsOf(UUID userId) {
        return memberships.findActiveClubIds(userId);
    }

    /** SPI for the club module (dependency inversion). */
    @Override
    public boolean canManageSettings(UUID clubId, Actor actor) {
        return hasPermission(clubId, actor, ClubPermission.CLUB_SETTINGS_MANAGE);
    }
}
