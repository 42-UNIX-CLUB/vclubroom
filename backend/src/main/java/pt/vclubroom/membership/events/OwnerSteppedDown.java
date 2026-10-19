package pt.vclubroom.membership.events;

import java.time.Instant;
import java.util.UUID;

public record OwnerSteppedDown(UUID clubId, UUID userId, Instant occurredAt) {
}
