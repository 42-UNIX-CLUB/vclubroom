package pt.vclubroom.calendar.application;

import java.time.Clock;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import pt.vclubroom.calendar.events.EventCancelled;
import pt.vclubroom.calendar.persistence.ClubEventRepository;
import pt.vclubroom.club.events.ClubArchived;

/**
 * R-10: an archived club's future events will not happen, so they are cancelled, each announced like a manual
 * cancellation (the notifications tell who planned to come). Past events stay as history. A second delivery finds
 * nothing scheduled any more: idempotent.
 */
@Component
class ClubArchivedListener {

    static final String REASON = "The club was archived.";

    private final ClubEventRepository events;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    ClubArchivedListener(ClubEventRepository events, ApplicationEventPublisher publisher, Clock clock) {
        this.events = events;
        this.publisher = publisher;
        this.clock = clock;
    }

    @ApplicationModuleListener
    void on(ClubArchived archived) {
        Instant now = clock.instant();
        events.findFutureScheduled(archived.clubId(), now).forEach(event -> {
            event.cancel(REASON, now);
            publisher.publishEvent(new EventCancelled(event.id(), event.clubId(), event.spaceId(), event.title(), event.startsAt(), REASON, now));
        });
    }
}
