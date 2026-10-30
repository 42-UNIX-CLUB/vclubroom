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
 * One message of a ticket's conversation. A team message comes from the tracker, so it has its comment ID at once; a
 * reporter's message gets it when TrackerSyncJob has sent it, with the same backoff as tickets.
 */
@Entity
@Table(name = "ticket_messages", schema = "support")
public class TicketMessage {

    @Id
    private UUID id;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageAuthor author;

    @Column(nullable = false)
    private String body;

    @Column(name = "tracker_comment_id")
    private Long trackerCommentId;

    @Column(name = "tracker_attempts", nullable = false)
    private int trackerAttempts;

    @Column(name = "tracker_next_attempt_at")
    private Instant trackerNextAttemptAt;

    @Column(name = "tracker_last_error")
    private String trackerLastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TicketMessage() {
        // JPA
    }

    public static TicketMessage fromReporter(UUID ticketId, String body, Instant now) {
        return of(ticketId, MessageAuthor.REPORTER, body, null, now);
    }

    public static TicketMessage fromTeam(UUID ticketId, String body, long commentId, Instant now) {
        return of(ticketId, MessageAuthor.TEAM, body, commentId, now);
    }

    private static TicketMessage of(UUID ticketId, MessageAuthor author, String body, Long commentId, Instant now) {
        TicketMessage m = new TicketMessage();
        m.id = UUID.randomUUID();
        m.ticketId = ticketId;
        m.author = author;
        m.body = body;
        m.trackerCommentId = commentId;
        m.createdAt = now;
        return m;
    }

    public UUID id() { return id; }
    public UUID ticketId() { return ticketId; }
    public MessageAuthor author() { return author; }
    public String body() { return body; }
    public Long trackerCommentId() { return trackerCommentId; }
    public int trackerAttempts() { return trackerAttempts; }
    public Instant createdAt() { return createdAt; }

    public void sentToTracker(long commentId) {
        this.trackerCommentId = commentId;
        this.trackerLastError = null;
        this.trackerNextAttemptAt = null;
    }

    /** Same waits as a ticket: 1, 2, 4… minutes, at most an hour. */
    public void trackerFailed(String error, Instant now) {
        this.trackerAttempts++;
        this.trackerLastError = error;
        this.trackerNextAttemptAt = now.plus(Duration.ofMinutes(Math.min(60, 1L << Math.min(trackerAttempts - 1, 6))));
    }
}
