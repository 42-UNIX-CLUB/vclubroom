package pt.vclubroom.calendar.web.dto;

import jakarta.validation.constraints.NotNull;
import pt.vclubroom.calendar.api.RsvpStatus;

public record RsvpRequest(@NotNull RsvpStatus status) {
}
