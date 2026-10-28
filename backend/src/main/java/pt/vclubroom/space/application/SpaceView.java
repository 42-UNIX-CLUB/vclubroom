package pt.vclubroom.space.application;

import java.util.List;
import java.util.UUID;
import pt.vclubroom.space.domain.AccessLevel;

/** A space the viewer can read, with its channels. Group IDs of GROUPS rules stay out (space settings, E05-S03). */
public record SpaceView(UUID id, String name, String slug, String description, int position,
                        AccessLevel readLevel, List<UUID> readGroupIds, AccessLevel writeLevel, List<UUID> writeGroupIds,
                        boolean archived, boolean canWrite, boolean canModerate, List<ChannelView> channels) {
}
