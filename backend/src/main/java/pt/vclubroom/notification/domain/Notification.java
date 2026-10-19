package pt.vclubroom.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One notification for one person. Rows are inserted by NotificationDispatcher (idempotent SQL); read here. */
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
}
