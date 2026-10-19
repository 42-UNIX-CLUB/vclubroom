package pt.vclubroom.calendar.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import pt.vclubroom.calendar.application.EventCommand;

/** The whole form again (the dialog sends every field); the space and the cover stay as they are. */
public record EditEventRequest(@NotBlank @Size(max = 140) String title,
                               @Size(max = 5000) String description,
                               @Size(max = 200) String location,
                               @Size(max = 500) @Pattern(regexp = "^(https?://.+)?$") String onlineUrl,
                               @NotNull Instant startsAt,
                               @NotNull Instant endsAt) {

    public EventCommand toCommand() {
        return new EventCommand(null, title, description, location, onlineUrl, startsAt, endsAt, null);
    }
}
