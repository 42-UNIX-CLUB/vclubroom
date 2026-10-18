package pt.vclubroom.calendar.events;

import java.time.Instant;
import java.util.UUID;

public record EventScheduled(UUID eventId, UUID clubId, UUID spaceId, String title, Instant startsAt, Instant endsAt,
                             UUID createdBy, Instant occurredAt) {
}
