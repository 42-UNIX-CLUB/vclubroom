package pt.vclubroom.calendar.web.dto;

import java.time.Instant;
import java.util.UUID;
import pt.vclubroom.calendar.application.EventDetails;

public record EventResponse(UUID id, UUID clubId, String clubSlug, String clubName, UUID spaceId, String title,
                            String description, String location, String onlineUrl, Instant startsAt, Instant endsAt,
                            String status, String cancelReason, String coverUrl, UUID createdBy,
                            long going, long interested, String myRsvp, boolean canManage, boolean acceptsRsvp) {

    public static EventResponse from(EventDetails d) {
        return new EventResponse(d.id(), d.clubId(), d.clubSlug(), d.clubName(), d.spaceId(), d.title(),
            d.description(), d.location(), d.onlineUrl(), d.startsAt(), d.endsAt(), d.status().name(),
            d.cancelReason(), d.coverUrl(), d.createdBy(), d.going(), d.interested(),
            d.myRsvp() != null ? d.myRsvp().name() : null, d.canManage(), d.acceptsRsvp());
    }
}
