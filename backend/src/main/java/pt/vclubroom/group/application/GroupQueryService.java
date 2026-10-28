package pt.vclubroom.group.application;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.group.api.GroupCatalog;
import pt.vclubroom.group.api.GroupMembershipQuery;
import pt.vclubroom.group.api.GroupScope;
import pt.vclubroom.group.api.GroupSummary;
import pt.vclubroom.group.domain.Group;
import pt.vclubroom.group.domain.GroupSource;
import pt.vclubroom.group.persistence.GroupMemberRepository;
import pt.vclubroom.group.persistence.GroupRepository;
import pt.vclubroom.shared.error.BusinessRuleException;

/** The group API other modules use (group.md §5): who is in which groups, and which groups a club may name. */
@Service
@Transactional(readOnly = true)
class GroupQueryService implements GroupMembershipQuery, GroupCatalog {

    private final GroupRepository groups;
    private final GroupMemberRepository members;

    GroupQueryService(GroupRepository groups, GroupMemberRepository members) {
        this.groups = groups;
        this.members = members;
    }

    /** No groups means nobody (R-23: a rule that names no group fails closed). */
    @Override
    public boolean isMemberOfAny(UUID userId, Collection<UUID> groupIds) {
        return !groupIds.isEmpty() && members.existsByUserInGroups(userId, groupIds);
    }

    @Override
    public Set<UUID> memberIdsOf(Collection<UUID> groupIds) {
        return groupIds.isEmpty() ? Set.of() : members.findUserIdsByGroups(groupIds);
    }

    /** BR-12: a club's spaces may name this club's groups and campus groups, nothing else. */
    @Override
    public void assertUsableInClub(UUID clubId, Collection<UUID> groupIds) {
        List<Group> found = groups.findByIdIn(groupIds);
        boolean allUsable = found.size() == Set.copyOf(groupIds).size()
            && found.stream().allMatch(g -> g.scope() == GroupScope.CAMPUS || g.belongsToClub(clubId));
        if (!allUsable) {
            throw new BusinessRuleException("GROUP_NOT_USABLE", "Spaces can only use this club's groups or campus groups.");
        }
    }

    @Override
    public List<GroupSummary> findByIds(Collection<UUID> groupIds) {
        return groups.findByIdIn(groupIds).stream()
            .map(g -> new GroupSummary(g.id(), g.scope(), g.clubId(), g.name(), g.slug(), g.source() == GroupSource.SYNCED))
            .toList();
    }
}
