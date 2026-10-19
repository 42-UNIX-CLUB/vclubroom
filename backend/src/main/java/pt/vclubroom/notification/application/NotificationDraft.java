package pt.vclubroom.notification.application;

import java.util.Map;
import pt.vclubroom.notification.domain.NotificationType;

/**
 * What to say. link is a SPA route ("/posts/<id>", "/c/<slug>"). dedupKey makes delivery idempotent per recipient
 * ("NEW_POST:<postId>"): a redelivered event inserts nothing and pushes nothing.
 */
public record NotificationDraft(NotificationType type, String title, String body, String link,
                                Map<String, Object> data, String dedupKey) {
}
