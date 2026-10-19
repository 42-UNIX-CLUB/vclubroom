package pt.vclubroom.group.application;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import pt.vclubroom.group.api.GroupCatalog;
import pt.vclubroom.group.api.GroupMembershipQuery;
import pt.vclubroom.group.api.GroupSummary;
import pt.vclubroom.shared.error.BusinessRuleException;

/**
 * TEMPORARY (E05-S01-T5): there are no groups until E13-S03, so nobody is in any group and no group can be used
 * in an access rule. E13-S03 implements group.api for real and deletes this class (two implementations of the
 * same interface would make Spring fail at start-up).
 */
@Component
class NoGroupsYet implements GroupMembershipQuery, GroupCatalog {

    @Override
    public boolean isMemberOfAny(UUID userId, Collection<UUID> groupIds) {
        return false;
    }

    @Override
    public Set<UUID> memberIdsOf(Collection<UUID> groupIds) {
        return Set.of();
    }

    @Override
    public void assertUsableInClub(UUID clubId, Collection<UUID> groupIds) {
        if (!groupIds.isEmpty()) {
            throw new BusinessRuleException("GROUP_NOT_USABLE", "Groups are not available yet.");
        }
    }

    @Override
    public List<GroupSummary> findByIds(Collection<UUID> groupIds) {
        return List.of();
    }
}
