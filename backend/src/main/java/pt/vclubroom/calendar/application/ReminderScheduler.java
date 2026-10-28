package pt.vclubroom.calendar.application;

import java.time.Clock;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.calendar.domain.EventReminder;
import pt.vclubroom.calendar.events.EventReminderDue;
import pt.vclubroom.calendar.persistence.ClubEventRepository;
import pt.vclubroom.calendar.persistence.EventReminderRepository;

/**
 * Sends due reminders (calendar.md §8.3), every minute on every backend: SKIP LOCKED gives each reminder to one of
 * them, and the event and sent_at commit together, so each goes out exactly once. A reminder of an event that no
 * longer takes place (cancelled, or its club archived) is marked sent without a word.
 */
@Component
class ReminderScheduler {

    static final int BATCH = 100;

    private final EventReminderRepository reminders;
    private final ClubEventRepository events;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    ReminderScheduler(EventReminderRepository reminders, ClubEventRepository events, ApplicationEventPublisher publisher, Clock clock) {
        this.reminders = reminders;
        this.events = events;
        this.publisher = publisher;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.calendar.reminder-interval:PT1M}", initialDelayString = "${app.calendar.reminder-interval:PT1M}")
    @Transactional
    public void sendDueReminders() {
        Instant now = clock.instant();
        for (EventReminder reminder : reminders.claimDue(now, BATCH)) {
            events.findById(reminder.eventId())
                .filter(e -> e.acceptsRsvp(now))
                .ifPresent(e -> publisher.publishEvent(new EventReminderDue(e.id(), e.clubId(), e.spaceId(), e.title(),
                    e.startsAt(), reminder.kind().name(), now)));
            reminder.markSent(now);
        }
    }
}
