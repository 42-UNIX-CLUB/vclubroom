package pt.vclubroom.calendar.api;

import java.util.Set;
import java.util.UUID;

public interface RsvpQuery {

    Set<UUID> attendees(UUID eventId, Set<RsvpStatus> statuses);
}
