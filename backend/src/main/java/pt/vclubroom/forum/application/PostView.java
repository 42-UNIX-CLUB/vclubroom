package pt.vclubroom.forum.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import pt.vclubroom.identity.api.UserSummary;
import pt.vclubroom.media.api.MediaView;

/**
 * A post as the viewer sees it. readOnly: no new comments or reactions (cannot write here, or an earlier era).
 * A deleted post keeps its place with body null. E09-S03 adds the reaction counts and my reactions.
 */
public record PostView(UUID id, UUID channelId, UUID clubId, int era, UserSummary author, String title, String body,
                       List<MediaView> media, int commentCount, boolean deleted, boolean deletedByModerator,
                       boolean readOnly, Instant createdAt, Instant editedAt) {
}
