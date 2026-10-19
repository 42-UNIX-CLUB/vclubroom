package pt.vclubroom.forum.application;

import java.time.Instant;
import java.util.UUID;
import pt.vclubroom.identity.api.UserSummary;

/** parentId null: a top-level comment. A deleted comment keeps its place with body null. E09-S03 adds reactions. */
public record CommentView(UUID id, UUID postId, UUID parentId, UserSummary author, String body, boolean deleted,
                          boolean deletedByModerator, Instant createdAt, Instant editedAt) {
}
