package pt.vclubroom.calendar.application;

import java.time.Clock;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.calendar.domain.ClubEvent;
import pt.vclubroom.calendar.domain.EventReminder;
import pt.vclubroom.calendar.events.EventReminderDue;
import pt.vclubroom.calendar.persistence.ClubEventRepository;
import pt.vclubroom.calendar.persistence.EventReminderRepository;

@Component
class ReminderScheduler {

    private static final int BATCH = 100;

    private final EventReminderRepository reminders;
    private final ClubEventRepository events;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    ReminderScheduler(EventReminderRepository reminders, ClubEventRepository events,
                      ApplicationEventPublisher publisher, Clock clock) {
        this.reminders = reminders;
        this.events = events;
        this.publisher = publisher;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT30S")
    @Transactional
    public void sendDueReminders() {
        Instant now = clock.instant();
        for (EventReminder reminder : reminders.claimDue(now, BATCH)) {           // FOR UPDATE SKIP LOCKED
            events.findById(reminder.eventId())
                .filter(e -> e.acceptsRsvp(now))
                .ifPresent((ClubEvent e) -> publisher.publishEvent(new EventReminderDue(
                    e.id(), e.clubId(), e.spaceId(), e.title(), e.startsAt(), reminder.kind().name(), now)));
            reminder.markSent(now);
        }
    }
}
