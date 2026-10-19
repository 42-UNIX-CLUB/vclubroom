package pt.vclubroom.club.events;

import java.time.Instant;
import java.util.UUID;

/**
 * membership makes the requester the owner and the previous owners members (R-05); history adds "Revived";
 * notification tells the members and the new owner. Discovery follows ClubProfileUpdated.
 */
public record ClubRevived(UUID clubId, String name, int newEra, UUID newOwnerId, UUID approvedBy,
                          UUID requestId, Instant occurredAt) {
}
