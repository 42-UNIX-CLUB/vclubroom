package pt.vclubroom.notification.web;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pt.vclubroom.notification.application.NotificationQueryService;
import pt.vclubroom.notification.application.NotificationView;
import pt.vclubroom.shared.security.AuthenticatedUser;
import pt.vclubroom.shared.web.CursorPage;

/** The inbox (notification.md §6). New notifications also arrive live on /user/queue/notifications. */
@RestController
@RequestMapping("/api/notifications")
class NotificationController {

    private final NotificationQueryService notifications;

    NotificationController(NotificationQueryService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    CursorPage<NotificationView> inbox(@AuthenticationPrincipal AuthenticatedUser me,
                                       @RequestParam(name = "unreadOnly", defaultValue = "false") boolean unreadOnly,
                                       @RequestParam(name = "cursor", required = false) String cursor,
                                       @RequestParam(name = "limit", defaultValue = "20") int limit) {
        return notifications.inbox(me.actor(), unreadOnly, cursor, Math.clamp(limit, 1, 50));
    }

    @GetMapping("/unread-count")
    Map<String, Long> unreadCount(@AuthenticationPrincipal AuthenticatedUser me) {
        return Map.of("count", notifications.unreadCount(me.actor()));
    }

    @PostMapping("/{notificationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void markRead(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("notificationId") UUID notificationId) {
        notifications.markRead(me.actor(), notificationId);
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void markAllRead(@AuthenticationPrincipal AuthenticatedUser me) {
        notifications.markAllRead(me.actor());
    }
}
