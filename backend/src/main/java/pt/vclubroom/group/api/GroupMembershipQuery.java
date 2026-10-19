package pt.vclubroom.group.api;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

public interface GroupMembershipQuery {

    /** Core check used by space access rules. Empty collection → false (fail closed, R-23). */
    boolean isMemberOfAny(UUID userId, Collection<UUID> groupIds);

    Set<UUID> memberIdsOf(Collection<UUID> groupIds);
}
