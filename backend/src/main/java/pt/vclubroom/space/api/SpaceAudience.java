package pt.vclubroom.space.api;

import java.util.Set;
import java.util.UUID;

public interface SpaceAudience {

    /** Active club members who can read the space (notification fan-out; never the whole campus). */
    Set<UUID> clubMembersWithReadAccess(UUID spaceId);
}
