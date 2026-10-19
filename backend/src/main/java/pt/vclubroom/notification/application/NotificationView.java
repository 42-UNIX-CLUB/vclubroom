package pt.vclubroom.notification.application;

import java.time.Instant;
import java.util.UUID;
import pt.vclubroom.notification.domain.NotificationType;

/** A notification in the inbox, and the payload of its live push. */
public record NotificationView(UUID id, NotificationType type, String title, String body, String link, boolean read,
                               Instant createdAt) {
}
