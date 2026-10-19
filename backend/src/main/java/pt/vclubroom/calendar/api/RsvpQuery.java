package pt.vclubroom.calendar.api;

import java.util.Set;
import java.util.UUID;

/** Who answered an event, for notifications (updated, cancelled) and reminders (E11-S04). */
public interface RsvpQuery {

    Set<UUID> attendees(UUID eventId, Set<RsvpStatus> statuses);
}
