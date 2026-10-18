package pt.vclubroom.calendar.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.calendar.domain.ClubEvent;
import pt.vclubroom.calendar.domain.EventReminder;
import pt.vclubroom.calendar.domain.ReminderKind;
import pt.vclubroom.calendar.events.EventCancelled;
import pt.vclubroom.calendar.events.EventScheduled;
import pt.vclubroom.calendar.events.EventUpdated;
import pt.vclubroom.calendar.persistence.ClubEventRepository;
import pt.vclubroom.calendar.persistence.EventReminderRepository;
import pt.vclubroom.media.api.MediaCatalog;
import pt.vclubroom.media.api.MediaPurpose;
import pt.vclubroom.membership.api.ClubAuthorization;
import pt.vclubroom.membership.api.ClubPermission;
import pt.vclubroom.shared.error.BusinessRuleException;
import pt.vclubroom.shared.security.Actor;
import pt.vclubroom.space.api.SpaceAccess;
import pt.vclubroom.space.api.SpaceAccessPolicy;

/** Event use cases that change state (calendar.md §8.1). Reminders follow the event's start time. */
@Service
public class EventService {

    private final ClubEventRepository events;
    private final EventReminderRepository reminders;
    private final EventAccess access;
    private final EventDetailsAssembler assembler;
    private final ClubAuthorization clubAuthorization;
    private final SpaceAccessPolicy spaceAccess;
    private final MediaCatalog media;
    private final CalendarProperties properties;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    public EventService(ClubEventRepository events, EventReminderRepository reminders, EventAccess access,
                        EventDetailsAssembler assembler, ClubAuthorization clubAuthorization,
                        SpaceAccessPolicy spaceAccess, MediaCatalog media, CalendarProperties properties,
                        ApplicationEventPublisher publisher, Clock clock) {
        this.events = events;
        this.reminders = reminders;
        this.access = access;
        this.assembler = assembler;
        this.clubAuthorization = clubAuthorization;
        this.spaceAccess = spaceAccess;
        this.media = media;
        this.properties = properties;
        this.publisher = publisher;
        this.clock = clock;
    }

    @Transactional
    public EventDetails schedule(Actor actor, UUID clubId, ScheduleEventCommand cmd) {
        clubAuthorization.require(clubId, actor, ClubPermission.EVENT_MANAGE);          // also checks club ACTIVE
        SpaceAccess space = spaceAccess.forSpace(cmd.spaceId(), actor);
        if (!space.clubId().equals(clubId)) {                                            // BR-21
            throw new BusinessRuleException("SPACE_NOT_IN_CLUB", "The space does not belong to this club.");
        }
        if (cmd.coverMediaId() != null) {
            media.attach(List.of(cmd.coverMediaId()), actor.userId(), MediaPurpose.EVENT_COVER);
        }
        Instant now = clock.instant();
        ClubEvent event = ClubEvent.schedule(clubId, cmd.spaceId(), space.currentEra(), actor.userId(), cmd.title(),
            cmd.description(), cmd.location(), cmd.onlineUrl(), cmd.startsAt(), cmd.endsAt(), cmd.coverMediaId(), now);
        events.save(event);
        createReminders(event, now);
        publisher.publishEvent(new EventScheduled(event.id(), event.clubId(), event.spaceId(), event.title(),
            event.startsAt(), event.endsAt(), actor.userId(), now));
        return assembler.toDetails(actor, event);
    }

    @Transactional
    public EventDetails edit(Actor actor, UUID eventId, EditEventCommand cmd) {
        ClubEvent event = access.require(eventId);
        clubAuthorization.require(event.clubId(), actor, ClubPermission.EVENT_MANAGE);
        Instant now = clock.instant();
        if (cmd.coverMediaId() != null && !cmd.coverMediaId().equals(event.coverMediaId())) {
            media.attach(List.of(cmd.coverMediaId()), actor.userId(), MediaPurpose.EVENT_COVER);
            event.changeCover(cmd.coverMediaId(), now);
        }
        Instant previousStart = event.startsAt();
        event.reschedule(
            cmd.title() != null ? cmd.title() : event.title(),
            cmd.description() != null ? cmd.description() : event.description(),
            cmd.location() != null ? cmd.location() : event.location(),
            cmd.onlineUrl() != null ? cmd.onlineUrl() : event.onlineUrl(),
            cmd.startsAt() != null ? cmd.startsAt() : event.startsAt(),
            cmd.endsAt() != null ? cmd.endsAt() : event.endsAt(),
            now);
        if (!event.startsAt().equals(previousStart)) {
            reminders.deleteUnsent(event.id());
            createReminders(event, now);
        }
        publisher.publishEvent(new EventUpdated(event.id(), event.clubId(), event.spaceId(), event.title(),
            event.startsAt(), event.endsAt(), now));
        return assembler.toDetails(actor, event);
    }

    @Transactional
    public void cancel(Actor actor, UUID eventId, String reason) {
        ClubEvent event = access.require(eventId);
        clubAuthorization.require(event.clubId(), actor, ClubPermission.EVENT_MANAGE);
        cancel(event, reason, clock.instant());
    }

    /** R-10: on archival, future SCHEDULED events are cancelled; past ones stay as history. Idempotent. */
    @Transactional
    public void cancelFutureForClub(UUID clubId, String reason) {
        Instant now = clock.instant();
        for (ClubEvent event : events.findFutureScheduled(clubId, now)) {
            cancel(event, reason, now);
        }
    }

    private void cancel(ClubEvent event, String reason, Instant now) {
        event.cancel(reason, now);
        reminders.deleteUnsent(event.id());
        publisher.publishEvent(new EventCancelled(event.id(), event.clubId(), event.title(), event.startsAt(),
            reason, now));
    }

    /** One reminder per configured offset that is still in the future (24 h and 1 h before by default). */
    private void createReminders(ClubEvent event, Instant now) {
        for (Duration offset : properties.reminderOffsets()) {
            Instant remindAt = event.startsAt().minus(offset);
            if (remindAt.isAfter(now)) {
                reminders.save(EventReminder.schedule(event.id(), ReminderKind.forOffset(offset), remindAt));
            }
        }
    }
}
