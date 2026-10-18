package pt.vclubroom.calendar.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import pt.vclubroom.shared.error.BusinessRuleException;
import pt.vclubroom.shared.error.InvalidRequestException;

@Entity
@Table(name = "events", schema = "calendar")
public class ClubEvent {

    @Id
    private UUID id;

    @Column(name = "club_id", nullable = false)
    private UUID clubId;

    @Column(name = "space_id", nullable = false)
    private UUID spaceId;

    @Column(nullable = false)
    private int era;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(nullable = false)
    private String title;

    private String description;

    private String location;

    @Column(name = "online_url")
    private String onlineUrl;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status;

    @Column(name = "cover_media_id")
    private UUID coverMediaId;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected ClubEvent() {
        // JPA
    }

    public static ClubEvent schedule(UUID clubId, UUID spaceId, int era, UUID createdBy, String title,
                                     String description, String location, String onlineUrl,
                                     Instant startsAt, Instant endsAt, UUID coverMediaId, Instant now) {
        validateTimes(startsAt, endsAt);
        ClubEvent e = new ClubEvent();
        e.id = UUID.randomUUID();
        e.clubId = clubId;
        e.spaceId = spaceId;
        e.era = era;
        e.createdBy = createdBy;
        e.title = title.strip();
        e.description = description;
        e.location = location;
        e.onlineUrl = onlineUrl;
        e.startsAt = startsAt.truncatedTo(ChronoUnit.MICROS);
        e.endsAt = endsAt.truncatedTo(ChronoUnit.MICROS);
        e.coverMediaId = coverMediaId;
        e.status = EventStatus.SCHEDULED;
        e.createdAt = now.truncatedTo(ChronoUnit.MICROS);
        e.updatedAt = e.createdAt;
        return e;
    }

    public void reschedule(String newTitle, String newDescription, String newLocation, String newOnlineUrl,
                           Instant newStartsAt, Instant newEndsAt, Instant now) {
        requireScheduled();
        validateTimes(newStartsAt, newEndsAt);
        this.title = newTitle.strip();
        this.description = newDescription;
        this.location = newLocation;
        this.onlineUrl = newOnlineUrl;
        this.startsAt = newStartsAt.truncatedTo(ChronoUnit.MICROS);
        this.endsAt = newEndsAt.truncatedTo(ChronoUnit.MICROS);
        this.updatedAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    public void cancel(String reason, Instant now) {
        requireScheduled();
        this.status = EventStatus.CANCELLED;
        this.cancelReason = reason;
        this.updatedAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    public void changeCover(UUID newCoverMediaId, Instant now) {
        requireScheduled();
        this.coverMediaId = newCoverMediaId;
        this.updatedAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    public boolean acceptsRsvp(Instant now) {
        return status == EventStatus.SCHEDULED && now.isBefore(endsAt);
    }

    private static void validateTimes(Instant startsAt, Instant endsAt) {
        if (startsAt == null || endsAt == null || !endsAt.isAfter(startsAt)) {                 // BR-21
            throw new InvalidRequestException("INVALID_EVENT_TIMES", "The event must end after it starts.");
        }
    }

    private void requireScheduled() {
        if (status != EventStatus.SCHEDULED) {
            throw new BusinessRuleException("EVENT_CANCELLED", "This event was cancelled.");
        }
    }

    public UUID id() { return id; }
    public UUID clubId() { return clubId; }
    public UUID spaceId() { return spaceId; }
    public int era() { return era; }
    public UUID createdBy() { return createdBy; }
    public String title() { return title; }
    public String description() { return description; }
    public String location() { return location; }
    public String onlineUrl() { return onlineUrl; }
    public Instant startsAt() { return startsAt; }
    public Instant endsAt() { return endsAt; }
    public EventStatus status() { return status; }
    public UUID coverMediaId() { return coverMediaId; }
    public String cancelReason() { return cancelReason; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}

