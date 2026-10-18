package pt.vclubroom.calendar.events;

import java.time.Instant;
import java.util.UUID;

public record EventUpdated(UUID eventId, UUID clubId, UUID spaceId, String title, Instant startsAt, Instant endsAt,
                           Instant occurredAt) {
}
