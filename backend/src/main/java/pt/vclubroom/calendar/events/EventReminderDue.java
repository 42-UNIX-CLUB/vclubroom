package pt.vclubroom.calendar.events;

import java.time.Instant;
import java.util.UUID;

public record EventReminderDue(UUID eventId, UUID clubId, UUID spaceId, String title, Instant startsAt, String kind,
                               Instant occurredAt) {
}
