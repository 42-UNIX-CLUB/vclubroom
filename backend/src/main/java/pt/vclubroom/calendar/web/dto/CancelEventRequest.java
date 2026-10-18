package pt.vclubroom.calendar.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The reason is shown to everyone who RSVPed. */
public record CancelEventRequest(@NotBlank @Size(max = 1000) String reason) {
}
