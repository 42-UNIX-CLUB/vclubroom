package pt.vclubroom.forum.events;

import java.time.Instant;
import java.util.UUID;

/** Consumed by notification (members who can read the space) and discovery (the club's last activity). */
public record PostPublished(UUID postId, UUID channelId, UUID spaceId, UUID clubId, UUID authorId, int era,
                            String title, String excerpt, Instant occurredAt) {
}
