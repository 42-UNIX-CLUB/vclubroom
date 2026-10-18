package pt.vclubroom.membership.events;

import java.time.Instant;
import java.util.UUID;

/** via: FOUNDED | OPEN_JOIN | APPROVED_REQUEST | REVIVAL | ADMIN */
public record MemberJoined(UUID clubId, UUID userId, UUID membershipId, String via, Instant occurredAt) {
}
