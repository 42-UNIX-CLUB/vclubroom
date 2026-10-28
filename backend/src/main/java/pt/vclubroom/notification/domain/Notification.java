package pt.vclubroom.notification.domain;

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
 * One notification for one person. Rows are inserted by NotificationDispatcher (idempotent SQL); read here. The email
 * columns are an outbox: EmailDispatchJob sends what was requested, retrying with backoff (R-27).
 */
@Entity
@Table(name = "notifications", schema = "notification")
public class Notification {

    @Id
    private UUID id;

    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(nullable = false)
    private String title;

    private String body;

    private String link;

    @Column(name = "in_app", nullable = false)
    private boolean inApp;

    @Column(name = "email_requested", nullable = false)
    private boolean emailRequested;

    @Column(name = "email_sent_at")
    private Instant emailSentAt;

    @Column(name = "email_attempts", nullable = false)
    private int emailAttempts;

    @Column(name = "email_next_attempt_at")
    private Instant emailNextAttemptAt;

    @Column(name = "email_last_error")
    private String emailLastError;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Notification() {
        // JPA
    }

    public UUID id() { return id; }
    public UUID recipientId() { return recipientId; }
    public NotificationType type() { return type; }
    public String title() { return title; }
    public String body() { return body; }
    public String link() { return link; }
    public Instant readAt() { return readAt; }
    public Instant createdAt() { return createdAt; }
    public int emailAttempts() { return emailAttempts; }

    public void emailSent(Instant now) {
        this.emailSentAt = now;
        this.emailLastError = null;
    }

    /** One more failed attempt; the next one waits 1, 2, 4, 8 then 16 minutes (R-27). */
    public void emailFailed(String error, Instant now) {
        this.emailAttempts++;
        this.emailLastError = error;
        this.emailNextAttemptAt = now.plus(Duration.ofMinutes(1L << (emailAttempts - 1)));
    }

    /** Nobody to send it to (no address, or a deactivated account): stop trying. */
    public void emailSkipped() {
        this.emailRequested = false;
    }
}
