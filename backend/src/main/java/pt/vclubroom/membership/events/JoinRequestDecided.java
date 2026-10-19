package pt.vclubroom.membership.events;

import java.time.Instant;
import java.util.UUID;

/** decidedBy is null when the system decided (switching the club to OPEN approves every pending request). */
public record JoinRequestDecided(UUID requestId, UUID clubId, UUID userId, boolean approved, UUID decidedBy,
                                 Instant occurredAt) {
}
