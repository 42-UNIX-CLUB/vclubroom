package pt.vclubroom.support.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * A ticket and its outbox state. Sending it to the tracker never gives up: the wait doubles after each failure, from
 * 1 minute up to an hour, so a tracker down for maintenance gets every ticket once it is back.
 */
@Entity
@Table(name = "tickets", schema = "support")
public class Ticket {

    private static final long MAX_BACKOFF_MINUTES = 60;

    @Id
    private UUID id;

    @Column(nullable = false)
    private long number;

    @Column(name = "reporter_id", nullable = false)
    private UUID reporterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketType type;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String description;

    @Column(name = "page_url")
    private String pageUrl;

    @Column(name = "user_agent")
    private String userAgent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status;

    @Column(name = "tracker_issue")
    private Integer trackerIssue;

    @Column(name = "tracker_attempts", nullable = false)
    private int trackerAttempts;

    @Column(name = "tracker_next_attempt_at")
    private Instant trackerNextAttemptAt;

    @Column(name = "tracker_last_error")
    private String trackerLastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Ticket() {
        // JPA
    }

    public static Ticket open(long number, UUID reporterId, TicketType type, String title, String description,
                              String pageUrl, String userAgent, Instant now) {
        Ticket t = new Ticket();
        t.id = UUID.randomUUID();
        t.number = number;
        t.reporterId = reporterId;
        t.type = type;
        t.title = title;
        t.description = description;
        t.pageUrl = pageUrl;
        t.userAgent = userAgent;
        t.status = TicketStatus.RECEIVED;
        t.createdAt = now;
        t.updatedAt = now;
        return t;
    }

    public UUID id() { return id; }
    public long number() { return number; }
    public UUID reporterId() { return reporterId; }
    public TicketType type() { return type; }
    public String title() { return title; }
    public String description() { return description; }
    public String pageUrl() { return pageUrl; }
    public String userAgent() { return userAgent; }
    public TicketStatus status() { return status; }
    public Integer trackerIssue() { return trackerIssue; }
    public int trackerAttempts() { return trackerAttempts; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }

    /** How people refer to it: "T-17". Not "#17", which the tracker would link to its own issue 17. */
    public String ref() {
        return "T-" + number;
    }

    public void sentToTracker(int issue, Instant now) {
        this.trackerIssue = issue;
        this.trackerLastError = null;
        this.trackerNextAttemptAt = null;
        if (status == TicketStatus.RECEIVED) {
            this.status = TicketStatus.OPEN;
        }
        this.updatedAt = now;
    }

    /** One more failed attempt; the next one waits 1, 2, 4… minutes, at most an hour. */
    public void trackerFailed(String error, Instant now) {
        this.trackerAttempts++;
        this.trackerLastError = error;
        long minutes = Math.min(MAX_BACKOFF_MINUTES, 1L << Math.min(trackerAttempts - 1, 6));
        this.trackerNextAttemptAt = now.plus(Duration.ofMinutes(minutes));
    }
}
