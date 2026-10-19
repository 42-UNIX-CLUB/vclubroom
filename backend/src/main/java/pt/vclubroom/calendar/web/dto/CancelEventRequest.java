package pt.vclubroom.calendar.web.dto;

import jakarta.validation.constraints.Size;

/** The reason is optional; it is shown on the event page. */
public record CancelEventRequest(@Size(max = 500) String reason) {
}
