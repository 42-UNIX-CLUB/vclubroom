package pt.vclubroom.calendar.events;

import java.time.Instant;
import java.util.UUID;

/** Notification tells the people who answered GOING or INTERESTED (E11-S03, E12). */
public record EventUpdated(UUID eventId, UUID clubId, UUID spaceId, String title, Instant startsAt, Instant endsAt,
                           Instant occurredAt) {
}
