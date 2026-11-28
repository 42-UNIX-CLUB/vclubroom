package pt.vclubroom.notification.application;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import pt.vclubroom.forum.events.CommentAdded;
import pt.vclubroom.forum.events.PostPublished;
import pt.vclubroom.notification.domain.NotificationType;
import pt.vclubroom.space.api.SpaceAudience;
import pt.vclubroom.shared.error.NotFoundException;
import pt.vclubroom.shared.security.Actor;
import pt.vclubroom.space.api.SpaceAccessPolicy;

/** New posts reach the club members who can read the space; comments reach the post's author, replies the parent's. */
@Component
class ForumNotificationListener {

    private final SpaceAudience audience;
    private final SpaceAccessPolicy spaceAccess;
    private final NotificationDispatcher dispatcher;

    ForumNotificationListener(SpaceAudience audience, SpaceAccessPolicy spaceAccess, NotificationDispatcher dispatcher) {
        this.audience = audience;
        this.spaceAccess = spaceAccess;
        this.dispatcher = dispatcher;
    }

    @ApplicationModuleListener
    void on(PostPublished e) {
        Set<UUID> recipients = new HashSet<>(audience.clubMembersWithReadAccess(e.spaceId()));
        recipients.remove(e.authorId());
        dispatcher.dispatch(new NotificationDraft(NotificationType.NEW_POST, e.title() != null ? e.title() : "New post",
            e.excerpt(), "/posts/" + e.postId(), Map.of("clubId", e.clubId(), "postId", e.postId()),
            "NEW_POST:" + e.postId()), recipients);
    }

    @ApplicationModuleListener
    void on(CommentAdded e) {
        if (e.postAuthorId() != null && !e.postAuthorId().equals(e.authorId()) && canRead(e.spaceId(), e.postAuthorId())) {
            dispatcher.dispatch(new NotificationDraft(NotificationType.NEW_COMMENT, "New comment on your post", e.excerpt(),
                "/posts/" + e.postId(), Map.of("postId", e.postId()), "NEW_COMMENT:" + e.commentId()), Set.of(e.postAuthorId()));
        }
        if (e.parentAuthorId() != null && !e.parentAuthorId().equals(e.authorId()) && canRead(e.spaceId(), e.parentAuthorId())) {
            dispatcher.dispatch(new NotificationDraft(NotificationType.NEW_REPLY, "New reply to your comment", e.excerpt(),
                "/posts/" + e.postId(), Map.of("postId", e.postId()), "NEW_REPLY:" + e.commentId()), Set.of(e.parentAuthorId()));
        }
    }

    /** Someone who left the club or lost access to the space hears no more about its threads. */
    private boolean canRead(UUID spaceId, UUID userId) {
        try {
            return spaceAccess.forSpace(spaceId, new Actor(userId, false)).canRead();
        } catch (NotFoundException e) {
            return false;
        }
    }
}
