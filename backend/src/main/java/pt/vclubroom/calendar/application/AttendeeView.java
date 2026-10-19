package pt.vclubroom.calendar.application;

import java.time.Instant;
import pt.vclubroom.calendar.api.RsvpStatus;
import pt.vclubroom.identity.api.UserSummary;

/** Someone who answered, and how. */
public record AttendeeView(UserSummary user, RsvpStatus status, Instant answeredAt) {
}
