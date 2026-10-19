package pt.vclubroom.membership.application;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import pt.vclubroom.membership.api.ClubPermission;

/**
 * The viewer's relation to one club (GET /api/clubs/{clubId}/membership/me). The SPA shows or hides controls
 * with it; the backend checks every action again anyway.
 */
public record MembershipView(boolean member, boolean owner, List<RoleView> roles, Set<ClubPermission> permissions,
                             PendingRequest pendingRequest, PendingInvitation pendingInvitation) {

    public record RoleView(UUID id, String name, String color, boolean system) { }

    public record PendingRequest(UUID id, Instant createdAt) { }

    public record PendingInvitation(UUID id, boolean transfer, Instant expiresAt) { }
}
