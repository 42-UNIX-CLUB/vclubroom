package pt.vclubroom.calendar.application;

import java.time.Clock;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import pt.vclubroom.calendar.persistence.RsvpRepository;
import pt.vclubroom.membership.events.MemberLeft;

/**
 * Someone who left a club, or was removed from it, no longer comes to its upcoming events: their answers go, so they
 * leave the attendee lists and get no reminders. Past answers stay as history. Idempotent.
 */
@Component
class MemberLeftListener {

    private final RsvpRepository rsvps;
    private final Clock clock;

    MemberLeftListener(RsvpRepository rsvps, Clock clock) {
        this.rsvps = rsvps;
        this.clock = clock;
    }

    @ApplicationModuleListener
    void on(MemberLeft left) {
        rsvps.deleteUpcomingInClub(left.clubId(), left.userId(), clock.instant());
    }
}
