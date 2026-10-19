package pt.vclubroom.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.UUID;

/** A person's override of a type's defaults; no row means the defaults. E12-S02 lets people edit them. */
@Entity
@Table(name = "preferences", schema = "notification")
@IdClass(NotificationPreference.Key.class)
public class NotificationPreference {

    public record Key(UUID userId, NotificationType type) implements Serializable { }

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Id
    @Enumerated(EnumType.STRING)
    private NotificationType type;

    @Column(name = "in_app", nullable = false)
    private boolean inApp;

    @Column(nullable = false)
    private boolean email;

    protected NotificationPreference() {
        // JPA
    }

    public UUID userId() { return userId; }
    public NotificationType type() { return type; }
    public boolean inApp() { return inApp; }
    public boolean email() { return email; }
}
