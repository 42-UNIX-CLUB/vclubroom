package pt.vclubroom.notification.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.notification.domain.Notification;
import pt.vclubroom.notification.persistence.NotificationRepository;
import pt.vclubroom.shared.security.Actor;
import pt.vclubroom.shared.web.CursorPage;
import pt.vclubroom.shared.web.TimeCursor;

/** My inbox: newest first, the unread count, marking as read. Nobody reads or marks someone else's. */
@Service
public class NotificationQueryService {

    private static final TimeCursor FIRST_PAGE = new TimeCursor(Instant.parse("9999-12-31T23:59:59Z"), new UUID(-1L, -1L));

    private final NotificationRepository notifications;
    private final Clock clock;

    NotificationQueryService(NotificationRepository notifications, Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CursorPage<NotificationView> inbox(Actor actor, boolean unreadOnly, String cursor, int limit) {
        TimeCursor after = cursor == null ? FIRST_PAGE : TimeCursor.decode(cursor);
        List<Notification> rows = notifications.findInbox(actor.userId(), unreadOnly, after.createdAt(), after.id(), Limit.of(limit + 1));
        List<Notification> page = rows.size() > limit ? rows.subList(0, limit) : rows;
        String next = rows.size() > limit ? new TimeCursor(page.getLast().createdAt(), page.getLast().id()).encode() : null;
        return new CursorPage<>(page.stream()
            .map(n -> new NotificationView(n.id(), n.type(), n.title(), n.body(), n.link(), n.readAt() != null, n.createdAt()))
            .toList(), next);
    }

    @Transactional(readOnly = true)
    public long unreadCount(Actor actor) {
        return notifications.countUnread(actor.userId());
    }

    @Transactional
    public void markRead(Actor actor, UUID notificationId) {
        notifications.markRead(notificationId, actor.userId(), clock.instant());
    }

    @Transactional
    public void markAllRead(Actor actor) {
        notifications.markAllRead(actor.userId(), clock.instant());
    }
}
