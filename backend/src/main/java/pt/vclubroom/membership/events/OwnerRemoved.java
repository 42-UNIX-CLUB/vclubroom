package pt.vclubroom.membership.events;

import java.time.Instant;
import java.util.UUID;

/** reason: ADMIN | REVIVAL */
public record OwnerRemoved(UUID clubId, UUID userId, String reason, UUID by, Instant occurredAt) {
}
