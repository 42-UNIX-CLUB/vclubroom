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
import pt.vclubroom.group.domain.GroupSource;
import pt.vclubroom.group.events.GroupDeleted;
import pt.vclubroom.group.persistence.GroupMemberRepository;
import pt.vclubroom.group.persistence.GroupRepository;
import pt.vclubroom.identity.api.UserDirectory;
import pt.vclubroom.identity.api.UserSummary;
import pt.vclubroom.shared.error.BusinessRuleException;
import pt.vclubroom.shared.error.ConflictException;
import pt.vclubroom.shared.error.NotFoundException;

/**
 * Campus groups (group.md §7, E15-S03). Everyone lists them, for access rules. Admins create, rename and delete
 * MANUAL ones and choose their members; SYNCED ones belong to the 42 sync and refuse every change (422 GROUP_SYNCED).
 */
@Service
public class CampusGroupService {

    private final GroupRepository groups;
    private final GroupMemberRepository members;
    private final UserDirectory users;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    CampusGroupService(GroupRepository groups, GroupMemberRepository members, UserDirectory users,
                       ApplicationEventPublisher events, Clock clock) {
        this.groups = groups;
        this.members = members;
        this.users = users;
        this.events = events;
        this.clock = clock;
    }

    /** By name, with their member counts. */
    @Transactional(readOnly = true)
    public List<CampusGroupView> list() {
        List<Group> found = groups.findByScopeAndClubIdOrderByNameAsc(GroupScope.CAMPUS, null);
        Map<UUID, Long> counts = found.isEmpty() ? Map.of() : members.countByGroups(found.stream().map(Group::id).toList())
            .stream().collect(Collectors.toMap(GroupMemberRepository.MemberCount::groupId, GroupMemberRepository.MemberCount::count));
        return found.stream().map(g -> view(g, counts.getOrDefault(g.id(), 0L))).toList();
    }

    @Transactional
    public CampusGroupView create(UUID adminId, String name, String description) {
        ensureNameFree(name.trim(), null);
        Group group = groups.save(Group.manualCampusGroup(name.trim(), uniqueSlug(name), blankToNull(description), adminId, clock.instant()));
        return view(group, 0);
    }

    @Transactional
    public CampusGroupView update(UUID groupId, String name, String description) {
        Group group = loadManual(groupId);
        if (name != null) {
            ensureNameFree(name.trim(), groupId);
        }
        group.describe(name == null ? null : name.trim(), description == null ? null : blankToNull(description));
        return view(group, members.countByGroups(List.of(groupId)).stream().mapToLong(GroupMemberRepository.MemberCount::count).sum());
    }

    /** Its members go with it; space removes it from every rule that named it (E13-S03's GroupDeleted). */
    @Transactional
    public void delete(UUID groupId) {
        Group group = loadManual(groupId);
        groups.delete(group);
        events.publishEvent(new GroupDeleted(group.id(), group.scope(), null, clock.instant()));
    }

    @Transactional(readOnly = true)
    public List<GroupMemberView> members(UUID groupId) {
        load(groupId);
        List<GroupMember> rows = members.findByGroupIdOrderByAddedAtAsc(groupId);
        Map<UUID, UserSummary> people = users.findByIds(rows.stream().map(GroupMember::userId).toList());
        return rows.stream()
            .filter(m -> people.containsKey(m.userId()))
            .map(m -> new GroupMemberView(people.get(m.userId()), m.addedAt()))
            .sorted(Comparator.comparing(v -> v.user().displayName(), String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    /** Anyone with an account; adding someone already in the group changes nothing. */
    @Transactional
    public void addMember(UUID adminId, UUID groupId, UUID userId) {
        loadManual(groupId);
        if (users.findById(userId).isEmpty()) {
            throw new BusinessRuleException("USER_NOT_FOUND", "There is no such user.");
        }
        if (!members.existsById(new GroupMember.Key(groupId, userId))) {
            members.save(GroupMember.of(groupId, userId, adminId, clock.instant()));
        }
    }

    @Transactional
    public void removeMember(UUID groupId, UUID userId) {
        loadManual(groupId);
        members.deleteById(new GroupMember.Key(groupId, userId));
    }

    private Group load(UUID groupId) {
        return groups.findById(groupId).filter(g -> g.scope() == GroupScope.CAMPUS)
            .orElseThrow(() -> new NotFoundException("GROUP_NOT_FOUND", "Group not found"));
    }

    private Group loadManual(UUID groupId) {
        Group group = load(groupId);
        group.ensureManuallyManaged();                                                // GROUP_SYNCED
        return group;
    }

    private void ensureNameFree(String name, UUID except) {
        boolean taken = groups.findByScopeAndClubIdOrderByNameAsc(GroupScope.CAMPUS, null).stream()
            .anyMatch(g -> !g.id().equals(except) && g.name().equalsIgnoreCase(name));
        if (taken) {
            throw new ConflictException("GROUP_NAME_TAKEN", "A campus group with this name already exists.");
        }
    }

    /** "Tutors" → "manual-tutors": never a slug the 42 sync could need (campus-…, pool-…, cursus-…, alumni). */
    private String uniqueSlug(String name) {
        String base = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        base = "manual-" + (base.isEmpty() ? "group" : base.substring(0, Math.min(base.length(), 60)));
        String slug = base;
        for (int n = 2; groups.existsByScopeAndClubIdAndSlug(GroupScope.CAMPUS, null, slug); n++) {
            slug = base + "-" + n;
        }
        return slug;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static CampusGroupView view(Group g, long memberCount) {
        return new CampusGroupView(g.id(), g.name(), g.slug(), g.description(), g.source() == GroupSource.SYNCED, memberCount);
    }
}
