package pt.vclubroom.calendar.events;

import java.time.Instant;
import java.util.UUID;

/** spaceId: whose readers may still hear of it (null in events published before it was added). */
public record EventCancelled(UUID eventId, UUID clubId, UUID spaceId, String title, Instant startsAt, String reason,
                             Instant occurredAt) {
}
