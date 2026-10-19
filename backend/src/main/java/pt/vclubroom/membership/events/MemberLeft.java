package pt.vclubroom.membership.events;

import java.time.Instant;
import java.util.UUID;

/** reason: LEFT | REMOVED */
public record MemberLeft(UUID clubId, UUID userId, UUID membershipId, String reason, UUID by, Instant occurredAt) {
}
