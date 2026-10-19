package pt.vclubroom.club.events;

import java.time.Instant;
import java.util.UUID;

/** notification tells the admins (E12). */
public record ClubRevivalRequested(UUID requestId, UUID clubId, String clubName, UUID requesterId, Instant occurredAt) {
}
