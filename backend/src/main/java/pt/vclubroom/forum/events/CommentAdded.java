package pt.vclubroom.forum.events;

import java.time.Instant;
import java.util.UUID;

/** Notification tells the post's author, and for a reply the parent comment's author (parentAuthorId, else null). */
public record CommentAdded(UUID commentId, UUID postId, UUID parentId, UUID clubId, UUID spaceId, UUID authorId,
                           UUID postAuthorId, UUID parentAuthorId, String excerpt, Instant occurredAt) {
}
