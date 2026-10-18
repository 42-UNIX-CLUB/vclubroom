package pt.vclubroom.membership.events;

import java.time.Instant;
import java.util.UUID;

/** reason: FOUNDER | INVITATION | REVIVAL | ADMIN */
public record OwnerAdded(UUID clubId, UUID userId, String reason, UUID by, Instant occurredAt) {
}
