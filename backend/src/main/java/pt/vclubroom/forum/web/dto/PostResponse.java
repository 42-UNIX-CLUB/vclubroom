package pt.vclubroom.forum.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import pt.vclubroom.forum.application.PostView;
import pt.vclubroom.identity.api.UserSummary;
import pt.vclubroom.media.api.MediaView;

/** The HTTP shape of a post, separate from PostView so the contract can evolve on its own (forum.md §9). */
public record PostResponse(UUID id, UUID channelId, UUID clubId, int era, UserSummary author, String title,
                           String body, List<MediaView> media, int commentCount, boolean deleted,
                           boolean deletedByModerator, boolean readOnly, Instant createdAt, Instant editedAt) {

    public static PostResponse from(PostView v) {
        return new PostResponse(v.id(), v.channelId(), v.clubId(), v.era(), v.author(), v.title(), v.body(), v.media(),
            v.commentCount(), v.deleted(), v.deletedByModerator(), v.readOnly(), v.createdAt(), v.editedAt());
    }
}
