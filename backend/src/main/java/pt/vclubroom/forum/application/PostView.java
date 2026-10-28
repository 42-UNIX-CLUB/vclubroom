package pt.vclubroom.forum.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import pt.vclubroom.identity.api.UserSummary;
import pt.vclubroom.media.api.MediaView;

/**
 * A post as the viewer sees it. readOnly: no new comments or reactions (cannot write here, or an earlier era).
 * A deleted post keeps its place with body null. reactionCounts: code → people; myReactions: the viewer's codes.
 * canEdit, canDelete and canPin say which actions this viewer may take, so the SPA never guesses (E09-S04).
 */
public record PostView(UUID id, UUID channelId, UUID clubId, int era, UserSummary author, String title, String body,
                       List<MediaView> media, boolean pinned, int commentCount, Map<String, Long> reactionCounts,
                       Set<String> myReactions, boolean deleted, boolean deletedByModerator, boolean readOnly,
                       boolean canEdit, boolean canDelete, boolean canPin, Instant createdAt, Instant editedAt) {
}
