package pt.vclubroom.membership.events;

import java.time.Instant;
import java.util.UUID;

public record JoinRequested(UUID requestId, UUID clubId, UUID userId, Instant occurredAt) {
}
