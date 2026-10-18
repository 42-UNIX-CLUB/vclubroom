package pt.vclubroom.calendar.application;

import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import pt.vclubroom.calendar.domain.ClubEvent;
import pt.vclubroom.calendar.persistence.ClubEventRepository;
import pt.vclubroom.shared.error.NotFoundException;
import pt.vclubroom.shared.security.Actor;
import pt.vclubroom.space.api.SpaceAccessPolicy;

/** Events inherit the read rule of their space (R-06). An unreadable event answers like a missing one. */
@Component
class EventAccess {

    private final ClubEventRepository events;
    private final SpaceAccessPolicy spaceAccess;

    EventAccess(ClubEventRepository events, SpaceAccessPolicy spaceAccess) {
        this.events = events;
        this.spaceAccess = spaceAccess;
    }

    ClubEvent requireReadable(UUID eventId, Actor actor) {
        return events.findById(eventId)
            .filter(e -> spaceAccess.filterReadableSpaces(actor, Set.of(e.spaceId())).contains(e.spaceId()))
            .orElseThrow(() -> new NotFoundException("EVENT_NOT_FOUND", "Event not found"));
    }

    ClubEvent require(UUID eventId) {
        return events.findById(eventId)
            .orElseThrow(() -> new NotFoundException("EVENT_NOT_FOUND", "Event not found"));
    }
}
