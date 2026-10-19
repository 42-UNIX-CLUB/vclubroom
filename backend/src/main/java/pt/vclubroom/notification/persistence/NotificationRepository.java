package pt.vclubroom.notification.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pt.vclubroom.notification.domain.Notification;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /** The inbox: in-app notifications, newest first, keyset (ix_notifications_inbox). */
    @Query("""
           select n from Notification n
           where n.recipientId = :recipientId and n.inApp = true
             and (:unreadOnly = false or n.readAt is null)
             and (n.createdAt < :cursorAt or (n.createdAt = :cursorAt and n.id < :cursorId))
           order by n.createdAt desc, n.id desc
           """)
    List<Notification> findInbox(@Param("recipientId") UUID recipientId, @Param("unreadOnly") boolean unreadOnly,
                                 @Param("cursorAt") Instant cursorAt, @Param("cursorId") UUID cursorId, Limit limit);

    @Query("select count(n) from Notification n where n.recipientId = :recipientId and n.inApp = true and n.readAt is null")
    long countUnread(@Param("recipientId") UUID recipientId);

    /** Only the recipient's own: someone else's ID changes nothing. */
    @Modifying
    @Query("update Notification n set n.readAt = :now where n.id = :id and n.recipientId = :recipientId and n.readAt is null")
    int markRead(@Param("id") UUID id, @Param("recipientId") UUID recipientId, @Param("now") Instant now);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.recipientId = :recipientId and n.inApp = true and n.readAt is null")
    int markAllRead(@Param("recipientId") UUID recipientId, @Param("now") Instant now);
}
