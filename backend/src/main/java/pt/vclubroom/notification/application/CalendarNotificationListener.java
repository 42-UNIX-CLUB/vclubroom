package pt.vclubroom.notification.application;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import pt.vclubroom.calendar.api.RsvpQuery;
import pt.vclubroom.calendar.api.RsvpStatus;
import pt.vclubroom.calendar.events.EventCancelled;
import pt.vclubroom.calendar.events.EventScheduled;
import pt.vclubroom.calendar.events.EventUpdated;
import pt.vclubroom.notification.domain.NotificationType;
import pt.vclubroom.space.api.SpaceAudience;

/**
 * A new event reaches the club members who can read its space (R-06); a change or a cancellation reaches the people
 * who said they were going or interested. E11-S04 brings the reminders.
 */
@Component
class CalendarNotificationListener {

    private static final Set<RsvpStatus> ANSWERED = Set.of(RsvpStatus.GOING, RsvpStatus.INTERESTED);

    private final SpaceAudience audience;
    private final RsvpQuery rsvps;
    private final NotificationDispatcher dispatcher;

    CalendarNotificationListener(SpaceAudience audience, RsvpQuery rsvps, NotificationDispatcher dispatcher) {
        this.audience = audience;
        this.rsvps = rsvps;
        this.dispatcher = dispatcher;
    }

    @ApplicationModuleListener
    void on(EventScheduled e) {
        Set<UUID> recipients = new HashSet<>(audience.clubMembersWithReadAccess(e.spaceId()));
        recipients.remove(e.createdBy());
        dispatcher.dispatch(new NotificationDraft(NotificationType.EVENT_SCHEDULED, "New event: " + e.title(), null,
            "/events/" + e.eventId(), Map.of("eventId", e.eventId(), "startsAt", e.startsAt().toString()),
            "EVENT_SCHEDULED:" + e.eventId()), recipients);
    }

    /** One notice per change (the dedup key carries its time). */
    @ApplicationModuleListener
    void on(EventUpdated e) {
        dispatcher.dispatch(new NotificationDraft(NotificationType.EVENT_UPDATED, e.title() + " changed",
            "Check the new time and place.", "/events/" + e.eventId(), Map.of("eventId", e.eventId(), "startsAt", e.startsAt().toString()),
            "EVENT_UPDATED:" + e.eventId() + ":" + e.occurredAt()), rsvps.attendees(e.eventId(), ANSWERED));
    }

    @ApplicationModuleListener
    void on(EventCancelled e) {
        dispatcher.dispatch(new NotificationDraft(NotificationType.EVENT_CANCELLED, e.title() + " is cancelled",
            e.reason(), "/events/" + e.eventId(), Map.of("eventId", e.eventId()), "EVENT_CANCELLED:" + e.eventId()),
            rsvps.attendees(e.eventId(), ANSWERED));
    }
}
