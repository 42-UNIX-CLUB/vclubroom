package pt.vclubroom.calendar.application;

import java.time.Instant;
import java.util.UUID;

public record ScheduleEventCommand(UUID spaceId, String title, String description, String location, String onlineUrl,
                                   Instant startsAt, Instant endsAt, UUID coverMediaId) {
}
