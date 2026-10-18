package pt.vclubroom.club.events;

import java.time.Instant;
import java.util.UUID;

/** An admin rejected a club request (final, BR-18). Notification tells the founder why (E12). */
public record ClubRejected(UUID clubId, String name, UUID founderId, UUID rejectedBy, String reason,
                           Instant occurredAt) {
}
