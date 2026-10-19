package pt.vclubroom.calendar.application;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import pt.vclubroom.calendar.api.RsvpStatus;
import pt.vclubroom.calendar.domain.Rsvp;
import pt.vclubroom.calendar.persistence.RsvpRepository;

/** Counts and the viewer's own answer, for one event or a whole calendar in two queries. */
@Component
class RsvpSummary {

    record Of(long going, long interested, RsvpStatus mine) {
        static final Of NONE = new Of(0, 0, null);
    }

    private final RsvpRepository rsvps;

    RsvpSummary(RsvpRepository rsvps) {
        this.rsvps = rsvps;
    }

    Map<UUID, Of> forEvents(Collection<UUID> eventIds, UUID viewerId) {
        if (eventIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Map<RsvpStatus, Long>> counts = new HashMap<>();
        rsvps.countByEvents(eventIds).forEach(c -> counts.computeIfAbsent(c.eventId(), id -> new HashMap<>()).put(c.status(), c.count()));
        Map<UUID, RsvpStatus> mine = rsvps.mineByEvents(viewerId, eventIds).stream()
            .collect(Collectors.toMap(Rsvp::eventId, Rsvp::status));
        return eventIds.stream().distinct().collect(Collectors.toMap(Function.identity(), id -> {
            Map<RsvpStatus, Long> c = counts.getOrDefault(id, Map.of());
            return new Of(c.getOrDefault(RsvpStatus.GOING, 0L), c.getOrDefault(RsvpStatus.INTERESTED, 0L), mine.get(id));
        }));
    }

    Of forEvent(UUID eventId, UUID viewerId) {
        return forEvents(List.of(eventId), viewerId).getOrDefault(eventId, Of.NONE);
    }
}
