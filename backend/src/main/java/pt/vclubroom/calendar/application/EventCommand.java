package pt.vclubroom.calendar.application;

import java.time.Instant;
import java.util.UUID;

/** What an event manager fills in. spaceId and coverMediaId are only read when scheduling. */
public record EventCommand(UUID spaceId, String title, String description, String location, String onlineUrl,
                           Instant startsAt, Instant endsAt, UUID coverMediaId) {
}
