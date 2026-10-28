package pt.vclubroom.calendar.web.dto;

import java.time.Instant;

/** url: only right after creating it (it is never stored); createdAt: when the current feed was made, null if none. */
public record CalendarFeedResponse(String url, Instant createdAt) {
}
