package pt.vclubroom.calendar.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.calendar.api.RsvpQuery;
import pt.vclubroom.calendar.api.RsvpStatus;
import pt.vclubroom.calendar.domain.ClubEvent;
import pt.vclubroom.calendar.domain.EventStatus;
import pt.vclubroom.calendar.domain.Rsvp;
import pt.vclubroom.calendar.persistence.RsvpRepository;
import pt.vclubroom.identity.api.UserDirectory;
import pt.vclubroom.identity.api.UserSummary;
import pt.vclubroom.shared.error.BusinessRuleException;
import pt.vclubroom.shared.security.Actor;

/** RSVPs (BR-22) and the public RsvpQuery that notification uses for fan-out. */
@Service
@Transactional(readOnly = true)
public class RsvpService implements RsvpQuery {

    private final RsvpRepository rsvps;
    private final EventAccess access;
    private final UserDirectory users;
    private final Clock clock;

    public RsvpService(RsvpRepository rsvps, EventAccess access, UserDirectory users, Clock clock) {
        this.rsvps = rsvps;
        this.access = access;
        this.users = users;
        this.clock = clock;
    }

    /** Only readers of the space, and only until the event ends; a second answer replaces the first. */
    @Transactional
    public RsvpTally answer(Actor actor, UUID eventId, RsvpStatus status) {
        ClubEvent event = access.requireReadable(eventId, actor);
        Instant now = clock.instant();
        if (!event.acceptsRsvp(now)) {
            throw event.status() == EventStatus.CANCELLED
                ? new BusinessRuleException("EVENT_CANCELLED", "This event was cancelled.")
                : new BusinessRuleException("EVENT_ENDED", "This event has already ended.");
        }
        rsvps.findById(new Rsvp.Key(eventId, actor.userId())).ifPresentOrElse(
            r -> r.change(status, now),
            () -> rsvps.save(Rsvp.answer(eventId, actor.userId(), status, now)));
        return tally(Set.of(eventId), actor.userId()).get(eventId);
    }

    /** Withdrawing is always allowed and idempotent. */
    @Transactional
    public void withdraw(Actor actor, UUID eventId) {
        rsvps.deleteById(new Rsvp.Key(eventId, actor.userId()));
    }

    /** Attendee list in sign-up order, for readers of the event's space. */
    public List<UserSummary> attendeeSummaries(Actor actor, UUID eventId, Set<RsvpStatus> statuses) {
        access.requireReadable(eventId, actor);
        if (statuses.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = rsvps.findUserIds(eventId, statuses);
        Map<UUID, UserSummary> byId = users.findByIds(ids);
        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    @Override
    public Set<UUID> attendees(UUID eventId, Set<RsvpStatus> statuses) {
        if (statuses.isEmpty()) {
            return Set.of();
        }
        return new LinkedHashSet<>(rsvps.findUserIds(eventId, statuses));
    }

    /** Batch counts and the viewer's own answers; every requested event gets an entry. */
    public Map<UUID, RsvpTally> tally(Collection<UUID> eventIds, UUID viewerId) {
        if (eventIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Map<RsvpStatus, Long>> counts = rsvps.countByEvents(eventIds).stream()
            .collect(Collectors.groupingBy(RsvpRepository.StatusCount::getEventId,
                Collectors.toMap(RsvpRepository.StatusCount::getStatus, RsvpRepository.StatusCount::getTotal)));
        Map<UUID, RsvpStatus> mine = rsvps.mineByEvents(viewerId, eventIds).stream()
            .collect(Collectors.toMap(Rsvp::eventId, Rsvp::status));
        Map<UUID, RsvpTally> result = new HashMap<>();
        for (UUID id : eventIds) {
            Map<RsvpStatus, Long> c = counts.getOrDefault(id, Map.of());
            result.put(id, new RsvpTally(c.getOrDefault(RsvpStatus.GOING, 0L),
                c.getOrDefault(RsvpStatus.INTERESTED, 0L), mine.get(id)));
        }
        return result;
    }
}
