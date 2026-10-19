package pt.vclubroom.notification.application;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import pt.vclubroom.calendar.events.EventScheduled;
import pt.vclubroom.notification.domain.NotificationType;
import pt.vclubroom.space.api.SpaceAudience;

/**
 * A new event reaches the club members who can read its space (R-06). E11-S03 (RSVPs) brings the updated and
 * cancelled notices for the people who answered, E11-S04 the reminders.
 */
@Component
class CalendarNotificationListener {

    private final SpaceAudience audience;
    private final NotificationDispatcher dispatcher;

    CalendarNotificationListener(SpaceAudience audience, NotificationDispatcher dispatcher) {
        this.audience = audience;
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
}
