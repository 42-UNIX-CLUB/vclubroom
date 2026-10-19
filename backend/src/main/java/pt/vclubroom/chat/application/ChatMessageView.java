package pt.vclubroom.chat.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import pt.vclubroom.identity.api.UserSummary;
import pt.vclubroom.media.api.MediaView;

/** The same for every viewer, so it can be pushed as it is. A deleted message keeps its place with body null. */
public record ChatMessageView(long id, UUID channelId, int era, UserSummary author, String body, List<MediaView> media,
                              UUID clientNonce, boolean deleted, Instant createdAt, Instant editedAt) {
}
