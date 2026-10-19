package pt.vclubroom.calendar.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import pt.vclubroom.calendar.api.RsvpStatus;

/** One person's answer to one event; answering again changes it. */
@Entity
@Table(name = "rsvps", schema = "calendar")
@IdClass(Rsvp.Key.class)
public class Rsvp {

    public record Key(UUID eventId, UUID userId) implements Serializable { }

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RsvpStatus status;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Rsvp() {
        // JPA
    }

    public static Rsvp answer(UUID eventId, UUID userId, RsvpStatus status, Instant now) {
        Rsvp r = new Rsvp();
        r.eventId = eventId;
        r.userId = userId;
        r.change(status, now);
        return r;
    }

    public void change(RsvpStatus newStatus, Instant now) {
        this.status = newStatus;
        this.updatedAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    public UUID eventId() { return eventId; }
    public UUID userId() { return userId; }
    public RsvpStatus status() { return status; }
    public Instant updatedAt() { return updatedAt; }
}
