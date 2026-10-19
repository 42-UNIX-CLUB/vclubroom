package pt.vclubroom.calendar.application;

import java.time.Instant;
import java.util.UUID;
import pt.vclubroom.calendar.api.RsvpStatus;
import pt.vclubroom.calendar.domain.EventStatus;

/** One event on a calendar, shaped for FullCalendar (start, end), with its RSVP counts and the viewer's own answer. */
public record CalendarItem(UUID id, String title, Instant start, Instant end, UUID clubId, String clubSlug,
                           String clubName, EventStatus status, long going, long interested, RsvpStatus myRsvp) {
}
