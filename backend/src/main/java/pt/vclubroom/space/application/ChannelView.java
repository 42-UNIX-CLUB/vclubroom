package pt.vclubroom.space.application;

import java.util.UUID;
import pt.vclubroom.space.api.ChannelType;

/** A channel with what the viewer may do in it. currentEra lets forum and chat mark older content read-only (BR-15). */
public record ChannelView(UUID id, UUID spaceId, String spaceSlug, String spaceName, ChannelType type, String name,
                          String slug, int position, boolean archived, boolean canWrite, boolean canModerate,
                          int currentEra) {
}
