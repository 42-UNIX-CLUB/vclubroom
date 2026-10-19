package pt.vclubroom.club.events;

import java.time.Instant;
import java.util.UUID;

/**
 * membership cancels pending requests and invitations (R-10); calendar cancels future events (E11-S06);
 * history adds "Archived" (E14-S03); notification informs the members (E12). Discovery follows ClubProfileUpdated.
 */
public record ClubArchived(UUID clubId, String name, int endedEra, UUID archivedBy, String reason, Instant occurredAt) {
}
