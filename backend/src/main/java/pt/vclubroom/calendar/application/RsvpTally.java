package pt.vclubroom.calendar.application;

import pt.vclubroom.calendar.api.RsvpStatus;

/** RSVP counts of one event, plus the viewer's own answer (null when they have not answered). */
public record RsvpTally(long going, long interested, RsvpStatus myRsvp) {
}
