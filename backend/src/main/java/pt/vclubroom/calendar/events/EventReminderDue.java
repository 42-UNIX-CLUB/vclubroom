package pt.vclubroom.calendar.events;

import java.time.Instant;
import java.util.UUID;

/** A reminder is due (E11-S04): notification tells the people who answered GOING or INTERESTED. kind: DAY_BEFORE or HOUR_BEFORE. */
public record EventReminderDue(UUID eventId, UUID clubId, UUID spaceId, String title, Instant startsAt, String kind,
                               Instant occurredAt) {
}
