package pt.vclubroom.club.events;

import java.time.Instant;
import java.util.UUID;

/** An admin approved a club: it is ACTIVE and era 1 has started. Membership makes the founder Owner (E07-S01). */
public record ClubApproved(UUID clubId, String slug, String name, UUID founderId, UUID approvedBy,
                           int era, Instant occurredAt) {
}
