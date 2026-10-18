package pt.vclubroom.calendar.web.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import pt.vclubroom.calendar.application.EditEventCommand;

/** PATCH body: omitted (null) fields keep their current value. */
public record EditEventRequest(
    @Size(max = 140) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String title,
    @Size(max = 5000) String description,
    @Size(max = 200) String location,
    @Size(max = 2000) @Pattern(regexp = "https?://\\S+") String onlineUrl,
    Instant startsAt,
    Instant endsAt,
    UUID coverMediaId) {

    public EditEventCommand toCommand() {
        return new EditEventCommand(title, description, location, onlineUrl, startsAt, endsAt, coverMediaId);
    }
}
