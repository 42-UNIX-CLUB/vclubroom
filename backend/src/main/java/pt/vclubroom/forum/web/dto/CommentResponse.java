package pt.vclubroom.forum.web.dto;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import pt.vclubroom.forum.application.CommentView;
import pt.vclubroom.identity.api.UserSummary;

public record CommentResponse(UUID id, UUID postId, UUID parentId, UserSummary author, String body,
                              Map<String, Long> reactionCounts, Set<String> myReactions, boolean deleted,
                              boolean deletedByModerator, boolean canEdit, boolean canDelete, Instant createdAt,
                              Instant editedAt) {

    public static CommentResponse from(CommentView v) {
        return new CommentResponse(v.id(), v.postId(), v.parentId(), v.author(), v.body(), v.reactionCounts(),
            v.myReactions(), v.deleted(), v.deletedByModerator(), v.canEdit(), v.canDelete(), v.createdAt(), v.editedAt());
    }
}
