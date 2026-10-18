package pt.vclubroom.calendar.application;

import java.time.Instant;
import java.util.UUID;
import pt.vclubroom.calendar.api.RsvpStatus;
import pt.vclubroom.calendar.domain.EventStatus;

/** Everything the event page shows, as seen by one actor. */
public record EventDetails(UUID id, UUID clubId, String clubSlug, String clubName, UUID spaceId, String title,
                           String description, String location, String onlineUrl, Instant startsAt, Instant endsAt,
                           EventStatus status, String cancelReason, String coverUrl, UUID createdBy,
                           long going, long interested, RsvpStatus myRsvp, boolean canManage, boolean acceptsRsvp) {
}
