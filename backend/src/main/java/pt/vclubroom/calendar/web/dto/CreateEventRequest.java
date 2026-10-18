package pt.vclubroom.calendar.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import pt.vclubroom.calendar.application.ScheduleEventCommand;

public record CreateEventRequest(
    @NotNull UUID spaceId,
    @NotBlank @Size(max = 140) String title,
    @Size(max = 5000) String description,
    @Size(max = 200) String location,
    @Size(max = 2000) @Pattern(regexp = "https?://\\S+") String onlineUrl,
    @NotNull Instant startsAt,
    @NotNull Instant endsAt,
    UUID coverMediaId) {

    public ScheduleEventCommand toCommand() {
        return new ScheduleEventCommand(spaceId, title, description, location, onlineUrl, startsAt, endsAt,
            coverMediaId);
    }
}
