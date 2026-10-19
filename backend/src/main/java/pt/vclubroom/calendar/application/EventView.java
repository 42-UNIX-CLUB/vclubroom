package pt.vclubroom.calendar.application;

import java.time.Instant;
import java.util.UUID;
import pt.vclubroom.calendar.api.RsvpStatus;
import pt.vclubroom.calendar.domain.EventStatus;
import pt.vclubroom.media.api.MediaView;

/**
 * An event as the viewer sees it. canManage: may edit or cancel it now; canRsvp: may answer now (BR-22); myRsvp is
 * null until the viewer answers.
 */
public record EventView(UUID id, UUID clubId, String clubSlug, String clubName, UUID spaceId, int era, String title,
                        String description, String location, String onlineUrl, Instant startsAt, Instant endsAt,
                        EventStatus status, String cancelReason, MediaView cover, boolean canManage,
                        long going, long interested, RsvpStatus myRsvp, boolean canRsvp) {
}
