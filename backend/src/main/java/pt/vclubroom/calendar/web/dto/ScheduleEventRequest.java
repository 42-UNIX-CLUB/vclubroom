package pt.vclubroom.calendar.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import pt.vclubroom.calendar.application.EventCommand;

/** endsAt after startsAt is a domain rule (400 INVALID_EVENT_TIMES), so the message names it. */
public record ScheduleEventRequest(@NotNull UUID spaceId,
                                   @NotBlank @Size(max = 140) String title,
                                   @Size(max = 5000) String description,
                                   @Size(max = 200) String location,
                                   @Size(max = 500) @Pattern(regexp = "^(https?://.+)?$") String onlineUrl,
                                   @NotNull Instant startsAt,
                                   @NotNull Instant endsAt,
                                   UUID coverMediaId) {

    public EventCommand toCommand() {
        return new EventCommand(spaceId, title, description, location, onlineUrl, startsAt, endsAt, coverMediaId);
    }
}
