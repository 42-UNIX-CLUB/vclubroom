package pt.vclubroom.forum.application;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import pt.vclubroom.identity.api.UserSummary;

/**
 * parentId null: a top-level comment. A deleted comment keeps its place with body null, and its replies stay.
 * canEdit and canDelete say what this viewer may do (E09-S04).
 */
public record CommentView(UUID id, UUID postId, UUID parentId, UserSummary author, String body,
                          Map<String, Long> reactionCounts, Set<String> myReactions, boolean deleted,
                          boolean deletedByModerator, boolean canEdit, boolean canDelete, Instant createdAt,
                          Instant editedAt) {
}
