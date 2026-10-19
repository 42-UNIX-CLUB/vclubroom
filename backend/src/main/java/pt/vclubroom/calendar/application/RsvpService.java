package pt.vclubroom.calendar.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.calendar.api.RsvpQuery;
import pt.vclubroom.calendar.api.RsvpStatus;
import pt.vclubroom.calendar.domain.ClubEvent;
import pt.vclubroom.calendar.domain.Rsvp;
import pt.vclubroom.calendar.persistence.ClubEventRepository;
import pt.vclubroom.calendar.persistence.RsvpRepository;
import pt.vclubroom.identity.api.UserDirectory;
import pt.vclubroom.identity.api.UserSummary;
import pt.vclubroom.shared.error.BusinessRuleException;
import pt.vclubroom.shared.error.NotFoundException;
import pt.vclubroom.shared.security.Actor;
import pt.vclubroom.space.api.SpaceAccessPolicy;

/**
 * Going, interested, not going (BR-22): only readers of the event's space, only while it is scheduled and has not
 * ended. Answers return the event with its new counts, so the page updates at once.
 */
@Service
public class RsvpService implements RsvpQuery {

    private final RsvpRepository rsvps;
    private final ClubEventRepository events;
    private final EventService eventService;
    private final SpaceAccessPolicy spaceAccess;
    private final UserDirectory users;
    private final Clock clock;

    RsvpService(RsvpRepository rsvps, ClubEventRepository events, EventService eventService, SpaceAccessPolicy spaceAccess,
                UserDirectory users, Clock clock) {
        this.rsvps = rsvps;
        this.events = events;
        this.eventService = eventService;
        this.spaceAccess = spaceAccess;
        this.users = users;
        this.clock = clock;
    }

    @Transactional
    public EventView answer(Actor actor, UUID eventId, RsvpStatus status) {
        ClubEvent event = openEvent(actor, eventId);
        Instant now = clock.instant();
        rsvps.findByEventIdAndUserId(event.id(), actor.userId())
            .ifPresentOrElse(r -> r.change(status, now), () -> rsvps.save(Rsvp.answer(event.id(), actor.userId(), status, now)));
        rsvps.flush();                                                     // the counts below include it
        return eventService.get(actor, eventId);
    }

    @Transactional
    public EventView withdraw(Actor actor, UUID eventId) {
        ClubEvent event = openEvent(actor, eventId);
        rsvps.findByEventIdAndUserId(event.id(), actor.userId()).ifPresent(r -> {
            rsvps.delete(r);
            rsvps.flush();
        });
        return eventService.get(actor, eventId);
    }

    /** Readers of the space see who answered, by name. */
    @Transactional(readOnly = true)
    public List<AttendeeView> attendees(Actor actor, UUID eventId, Set<RsvpStatus> statuses) {
        ClubEvent event = load(eventId);
        spaceAccess.forSpace(event.spaceId(), actor).requireRead();
        List<Rsvp> answers = rsvps.findByEventIdAndStatusIn(eventId, statuses);
        Map<UUID, UserSummary> people = users.findByIds(answers.stream().map(Rsvp::userId).toList());
        return answers.stream()
            .filter(r -> people.containsKey(r.userId()))
            .map(r -> new AttendeeView(people.get(r.userId()), r.status(), r.updatedAt()))
            .sorted(Comparator.comparing((AttendeeView a) -> a.status().ordinal())
                .thenComparing(a -> a.user().displayName(), String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> attendees(UUID eventId, Set<RsvpStatus> statuses) {
        return rsvps.findByEventIdAndStatusIn(eventId, statuses).stream().map(Rsvp::userId).collect(Collectors.toSet());
    }

    private ClubEvent openEvent(Actor actor, UUID eventId) {
        ClubEvent event = load(eventId);
        spaceAccess.forSpace(event.spaceId(), actor).requireRead();       // SPACE_READ_DENIED
        if (!event.acceptsRsvp(clock.instant())) {
            throw new BusinessRuleException("RSVP_CLOSED", "This event is over or cancelled.");
        }
        return event;
    }

    private ClubEvent load(UUID eventId) {
        return events.findById(eventId).orElseThrow(() -> new NotFoundException("EVENT_NOT_FOUND", "Event not found"));
    }
}
