package pt.vclubroom.calendar.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** One pending notification before an event; claimed by ReminderScheduler with SKIP LOCKED (calendar.md §8.3). */
@Entity
@Table(name = "event_reminders", schema = "calendar")
public class EventReminder {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReminderKind kind;

    @Column(name = "remind_at", nullable = false)
    private Instant remindAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    protected EventReminder() {
        // JPA
    }

    public static EventReminder schedule(UUID eventId, ReminderKind kind, Instant remindAt) {
        EventReminder r = new EventReminder();
        r.id = UUID.randomUUID();
        r.eventId = eventId;
        r.kind = kind;
        r.remindAt = remindAt.truncatedTo(ChronoUnit.MICROS);
        return r;
    }

    public void markSent(Instant now) {
        this.sentAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    public UUID id() { return id; }
    public UUID eventId() { return eventId; }
    public ReminderKind kind() { return kind; }
    public Instant remindAt() { return remindAt; }
    public Instant sentAt() { return sentAt; }
}
