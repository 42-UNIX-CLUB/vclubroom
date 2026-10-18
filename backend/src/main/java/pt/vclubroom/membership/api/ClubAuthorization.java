package pt.vclubroom.membership.api;

import java.util.Set;
import java.util.UUID;
import pt.vclubroom.shared.security.Actor;

/**
 * Single entry point for club-level authorization. Every module asks here; nobody reads
 * membership tables directly. This is also the one place where a permission cache can be added.
 */
public interface ClubAuthorization {

    boolean isActiveMember(UUID clubId, UUID userId);

    boolean isOwner(UUID clubId, UUID userId);

    /** Raw effective permissions (union of held roles; all for owners and admins), regardless of club status. */
    Set<ClubPermission> permissionsOf(UUID clubId, Actor actor);

    boolean hasPermission(UUID clubId, Actor actor, ClubPermission permission);

    /**
     * For management/write actions: the club must be ACTIVE (BR-02) and the actor must hold the permission.
     * @throws pt.vclubroom.shared.error.BusinessRuleException CLUB_NOT_ACTIVE
     * @throws pt.vclubroom.shared.error.ForbiddenException    PERMISSION_DENIED
     */
    void require(UUID clubId, Actor actor, ClubPermission permission);

    Set<UUID> activeMemberIds(UUID clubId);

    /** Users holding a permission (owners included) — e.g. who gets notified about a join request. */
    Set<UUID> holdersOf(UUID clubId, ClubPermission permission);

    Set<UUID> clubIdsOf(UUID userId);
}
