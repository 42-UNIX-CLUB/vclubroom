package pt.vclubroom.calendar.events;

import java.time.Instant;
import java.util.UUID;

public record EventCancelled(UUID eventId, UUID clubId, String title, Instant startsAt, String reason,
                             Instant occurredAt) {
}
