package pt.vclubroom.membership.events;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** A member's roles were replaced (E13-S02). A permission cache would evict (club, user) here. */
public record MemberRolesChanged(UUID clubId, UUID userId, Set<UUID> roleIds, UUID by, Instant occurredAt) {
}
