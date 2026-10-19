package pt.vclubroom.calendar.application;

import java.time.Instant;
import java.util.UUID;
import pt.vclubroom.calendar.domain.EventStatus;

/** One event on a calendar, shaped for FullCalendar (start, end). E11-S03 adds the RSVP counts and mine. */
public record CalendarItem(UUID id, String title, Instant start, Instant end, UUID clubId, String clubSlug,
                           String clubName, EventStatus status) {
}
