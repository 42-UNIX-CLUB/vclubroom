package pt.vclubroom.calendar.application;

import java.time.Instant;
import java.util.UUID;

/** PATCH semantics: a null field keeps the current value. */
public record EditEventCommand(String title, String description, String location, String onlineUrl,
                               Instant startsAt, Instant endsAt, UUID coverMediaId) {
}
