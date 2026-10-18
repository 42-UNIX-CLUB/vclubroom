package pt.vclubroom.calendar.web.dto;

import pt.vclubroom.calendar.application.RsvpTally;

public record RsvpResponse(long going, long interested, String myRsvp) {

    public static RsvpResponse from(RsvpTally t) {
        return new RsvpResponse(t.going(), t.interested(), t.myRsvp() != null ? t.myRsvp().name() : null);
    }
}
