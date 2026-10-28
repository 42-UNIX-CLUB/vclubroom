package pt.vclubroom.group.application;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.group.api.GroupScope;
import pt.vclubroom.group.domain.Group;
import pt.vclubroom.group.domain.GroupMember;
import pt.vclubroom.group.events.GroupDeleted;
import pt.vclubroom.group.persistence.GroupMemberRepository;
import pt.vclubroom.group.persistence.GroupRepository;
import pt.vclubroom.identity.api.UserDirectory;
import pt.vclubroom.identity.api.UserSummary;
import pt.vclubroom.membership.api.ClubAuthorization;
import pt.vclubroom.membership.api.ClubPermission;
import pt.vclubroom.shared.error.BusinessRuleException;
import pt.vclubroom.shared.error.ConflictException;
import pt.vclubroom.shared.error.ForbiddenException;
import pt.vclubroom.shared.error.NotFoundException;
import pt.vclubroom.shared.security.Actor;

/**
 * Club groups (group.md §7, §8.2): members (and admins) see them; GROUP_MANAGE in an active club changes them. Only
 * active club members can be added (BR-13). Deleting a group tells space, which takes it out of its rules.
 */
@Service
public class ClubGroupService {

    private final GroupRepository groups;
    private final GroupMemberRepository members;
    private final ClubAuthorization authorization;
    private final UserDirectory users;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    ClubGroupService(GroupRepository groups, GroupMemberRepository members, ClubAuthorization authorization,
                     UserDirectory users, ApplicationEventPublisher events, Clock clock) {
        this.groups = groups;
        this.members = members;
        this.authorization = authorization;
        this.users = users;
        this.events = events;
        this.clock = clock;
    }

    /** By name, with their member counts. */
    @Transactional(readOnly = true)
    public List<ClubGroupView> list(Actor actor, UUID clubId) {
        requireMember(actor, clubId);
        List<Group> found = groups.findByScopeAndClubIdOrderByNameAsc(GroupScope.CLUB, clubId);
        Map<UUID, Long> counts = found.isEmpty() ? Map.of() : members.countByGroups(found.stream().map(Group::id).toList())
            .stream().collect(Collectors.toMap(GroupMemberRepository.MemberCount::groupId, GroupMemberRepository.MemberCount::count));
        return found.stream().map(g -> view(g, counts.getOrDefault(g.id(), 0L))).toList();
    }

    @Transactional
    public ClubGroupView create(Actor actor, UUID clubId, String name, String description) {
        authorization.require(clubId, actor, ClubPermission.GROUP_MANAGE);
        ensureNameFree(clubId, name.trim(), null);
        Group group = groups.save(Group.clubGroup(clubId, name.trim(), uniqueSlug(clubId, name), blankToNull(description),
            actor.userId(), clock.instant()));
        return view(group, 0);
    }

    @Transactional
    public ClubGroupView update(Actor actor, UUID clubId, UUID groupId, String name, String description) {
        authorization.require(clubId, actor, ClubPermission.GROUP_MANAGE);
        Group group = load(clubId, groupId);
        if (name != null) {
            ensureNameFree(clubId, name.trim(), groupId);
        }
        group.describe(name == null ? null : name.trim(), description == null ? null : blankToNull(description));
        return view(group, members.countByGroups(List.of(groupId)).stream().mapToLong(GroupMemberRepository.MemberCount::count).sum());
    }

    /** Its members go with it (cascade); space removes it from every rule that named it. */
    @Transactional
    public void delete(Actor actor, UUID clubId, UUID groupId) {
        authorization.require(clubId, actor, ClubPermission.GROUP_MANAGE);
        Group group = load(clubId, groupId);
        groups.delete(group);
        events.publishEvent(new GroupDeleted(group.id(), group.scope(), clubId, clock.instant()));
    }

    /** In the order they were added, with their names. */
    @Transactional(readOnly = true)
    public List<GroupMemberView> members(Actor actor, UUID clubId, UUID groupId) {
        requireMember(actor, clubId);
        load(clubId, groupId);
        List<GroupMember> rows = members.findByGroupIdOrderByAddedAtAsc(groupId);
        Map<UUID, UserSummary> people = users.findByIds(rows.stream().map(GroupMember::userId).toList());
        return rows.stream()
            .filter(m -> people.containsKey(m.userId()))
            .map(m -> new GroupMemberView(people.get(m.userId()), m.addedAt()))
            .sorted(Comparator.comparing(v -> v.user().displayName(), String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    /** Adding someone already in the group changes nothing. */
    @Transactional
    public void addMember(Actor actor, UUID clubId, UUID groupId, UUID userId) {
        authorization.require(clubId, actor, ClubPermission.GROUP_MANAGE);
        load(clubId, groupId);
        if (!authorization.isActiveMember(clubId, userId)) {
            throw new BusinessRuleException("NOT_A_MEMBER", "Only members of the club can be in its groups.");
        }
        if (!members.existsById(new GroupMember.Key(groupId, userId))) {
            members.save(GroupMember.of(groupId, userId, actor.userId(), clock.instant()));
        }
    }

    @Transactional
    public void removeMember(Actor actor, UUID clubId, UUID groupId, UUID userId) {
        authorization.require(clubId, actor, ClubPermission.GROUP_MANAGE);
        load(clubId, groupId);
        members.deleteById(new GroupMember.Key(groupId, userId));
    }

    private void requireMember(Actor actor, UUID clubId) {
        if (!actor.admin() && !authorization.isActiveMember(clubId, actor.userId())) {
            throw new ForbiddenException("MEMBERS_ONLY", "Only members can see the club's groups.");
        }
    }

    private Group load(UUID clubId, UUID groupId) {
        return groups.findById(groupId).filter(g -> g.belongsToClub(clubId))
            .orElseThrow(() -> new NotFoundException("GROUP_NOT_FOUND", "Group not found"));
    }

    private void ensureNameFree(UUID clubId, String name, UUID except) {
        boolean taken = groups.findByScopeAndClubIdOrderByNameAsc(GroupScope.CLUB, clubId).stream()
            .anyMatch(g -> !g.id().equals(except) && g.name().equalsIgnoreCase(name));
        if (taken) {
            throw new ConflictException("GROUP_NAME_TAKEN", "A group with this name already exists.");
        }
    }

    /** "Board" → "board", then "board-2", "board-3"… if a club already uses it. */
    private String uniqueSlug(UUID clubId, String name) {
        String base = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        base = base.isEmpty() ? "group" : base.substring(0, Math.min(base.length(), 70));
        String slug = base;
        for (int n = 2; groups.existsByScopeAndClubIdAndSlug(GroupScope.CLUB, clubId, slug); n++) {
            slug = base + "-" + n;
        }
        return slug;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static ClubGroupView view(Group g, long memberCount) {
        return new ClubGroupView(g.id(), g.name(), g.slug(), g.description(), memberCount);
    }
}
