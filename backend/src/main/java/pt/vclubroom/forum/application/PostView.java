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
 */
public record PostView(UUID id, UUID channelId, UUID clubId, int era, UserSummary author, String title, String body,
                       List<MediaView> media, int commentCount, Map<String, Long> reactionCounts,
                       Set<String> myReactions, boolean deleted, boolean deletedByModerator, boolean readOnly,
                       Instant createdAt, Instant editedAt) {
}
