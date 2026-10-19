package pt.vclubroom.space.application;

import java.util.List;
import java.util.UUID;
import pt.vclubroom.space.domain.AccessLevel;

/** A space the viewer can read, with its channels. Group IDs of GROUPS rules stay out (space settings, E05-S03). */
public record SpaceView(UUID id, String name, String slug, String description, int position,
                        AccessLevel readLevel, AccessLevel writeLevel, boolean archived,
                        boolean canWrite, boolean canModerate, List<ChannelView> channels) {
}
