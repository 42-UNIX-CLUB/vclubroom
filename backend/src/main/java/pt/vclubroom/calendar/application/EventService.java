package pt.vclubroom.calendar.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.calendar.domain.ClubEvent;
import pt.vclubroom.calendar.events.EventCancelled;
import pt.vclubroom.calendar.events.EventScheduled;
import pt.vclubroom.calendar.events.EventUpdated;
import pt.vclubroom.calendar.persistence.ClubEventRepository;
import pt.vclubroom.club.api.ClubCatalog;
import pt.vclubroom.club.api.ClubSnapshot;
import pt.vclubroom.media.api.MediaCatalog;
import pt.vclubroom.media.api.MediaPurpose;
import pt.vclubroom.media.api.MediaView;
import pt.vclubroom.membership.api.ClubAuthorization;
import pt.vclubroom.membership.api.ClubPermission;
import pt.vclubroom.shared.error.BusinessRuleException;
import pt.vclubroom.shared.error.NotFoundException;
import pt.vclubroom.shared.security.Actor;
import pt.vclubroom.space.api.SpaceAccess;
import pt.vclubroom.space.api.SpaceAccessPolicy;

/**
 * Scheduling, editing and cancelling club events (calendar.md §8.1). EVENT_MANAGE in an ACTIVE club for every
 * change; the space's read rule for reading (R-06). E11-S04 adds the reminders (created, regenerated, deleted here).
 */
@Service
public class EventService {

    private final ClubEventRepository events;
    private final ClubAuthorization clubAuthorization;
    private final SpaceAccessPolicy spaceAccess;
    private final ClubCatalog clubs;
    private final MediaCatalog mediaCatalog;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    EventService(ClubEventRepository events, ClubAuthorization clubAuthorization, SpaceAccessPolicy spaceAccess,
                 ClubCatalog clubs, MediaCatalog mediaCatalog, ApplicationEventPublisher publisher, Clock clock) {
        this.events = events;
        this.clubAuthorization = clubAuthorization;
        this.spaceAccess = spaceAccess;
        this.clubs = clubs;
        this.mediaCatalog = mediaCatalog;
        this.publisher = publisher;
        this.clock = clock;
    }

    @Transactional
    public EventView schedule(Actor actor, UUID clubId, EventCommand cmd) {
        clubAuthorization.require(clubId, actor, ClubPermission.EVENT_MANAGE);         // CLUB_NOT_ACTIVE, PERMISSION_DENIED
        SpaceAccess space = spaceAccess.forSpace(cmd.spaceId(), actor);                // SPACE_NOT_FOUND
        if (!space.clubId().equals(clubId)) {                                         // BR-21
            throw new BusinessRuleException("SPACE_NOT_IN_CLUB", "The space belongs to another club.");
        }
        space.requireRead();                                                           // no event where you cannot look
        Instant now = clock.instant();
        ClubEvent event = ClubEvent.schedule(clubId, space.spaceId(), space.currentEra(), actor.userId(), cmd.title(),
            cmd.description(), cmd.location(), cmd.onlineUrl(), cmd.startsAt(), cmd.endsAt(), cmd.coverMediaId(), now);
        if (cmd.coverMediaId() != null) {
            mediaCatalog.attach(List.of(cmd.coverMediaId()), actor.userId(), MediaPurpose.EVENT_COVER);   // BR-19
        }
        events.save(event);
        publisher.publishEvent(new EventScheduled(event.id(), clubId, event.spaceId(), event.title(),
            event.startsAt(), event.endsAt(), actor.userId(), now));
        return view(event, actor);
    }

    @Transactional
    public EventView edit(Actor actor, UUID eventId, EventCommand cmd) {
        ClubEvent event = load(eventId);
        clubAuthorization.require(event.clubId(), actor, ClubPermission.EVENT_MANAGE);
        Instant now = clock.instant();
        event.reschedule(cmd.title(), cmd.description(), cmd.location(), cmd.onlineUrl(), cmd.startsAt(), cmd.endsAt(), now);
        publisher.publishEvent(new EventUpdated(event.id(), event.clubId(), event.spaceId(), event.title(),
            event.startsAt(), event.endsAt(), now));
        return view(event, actor);
    }

    @Transactional
    public EventView cancel(Actor actor, UUID eventId, String reason) {
        ClubEvent event = load(eventId);
        clubAuthorization.require(event.clubId(), actor, ClubPermission.EVENT_MANAGE);
        Instant now = clock.instant();
        event.cancel(reason, now);                                                     // EVENT_CANCELLED if already
        publisher.publishEvent(new EventCancelled(event.id(), event.clubId(), event.title(), event.startsAt(),
            event.cancelReason(), now));
        return view(event, actor);
    }

    /** Anyone who can read the event's space (R-06), cancelled or not. */
    @Transactional(readOnly = true)
    public EventView get(Actor actor, UUID eventId) {
        ClubEvent event = load(eventId);
        spaceAccess.forSpace(event.spaceId(), actor).requireRead();                    // SPACE_READ_DENIED
        return view(event, actor);
    }

    private ClubEvent load(UUID eventId) {
        return events.findById(eventId)
            .orElseThrow(() -> new NotFoundException("EVENT_NOT_FOUND", "Event not found"));
    }

    private EventView view(ClubEvent e, Actor actor) {
        ClubSnapshot club = clubs.require(e.clubId());
        boolean canManage = e.isScheduled() && club.isActive()
            && clubAuthorization.hasPermission(e.clubId(), actor, ClubPermission.EVENT_MANAGE);
        MediaView cover = e.coverMediaId() == null ? null : mediaCatalog.resolve(List.of(e.coverMediaId())).get(e.coverMediaId());
        return new EventView(e.id(), e.clubId(), club.slug(), club.name(), e.spaceId(), e.era(), e.title(),
            e.description(), e.location(), e.onlineUrl(), e.startsAt(), e.endsAt(), e.status(), e.cancelReason(),
            cover, canManage);
    }
}
