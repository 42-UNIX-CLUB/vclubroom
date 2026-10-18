package pt.vclubroom.calendar.application;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import pt.vclubroom.club.events.ClubArchived;

/** R-10: an archived club holds no future events. */
@Component
class ClubArchivedListener {

    private final EventService events;

    ClubArchivedListener(EventService events) {
        this.events = events;
    }

    @ApplicationModuleListener
    void on(ClubArchived archived) {
        events.cancelFutureForClub(archived.clubId(), "The club was archived.");
    }
}
