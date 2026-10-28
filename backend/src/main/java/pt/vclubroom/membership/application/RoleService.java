package pt.vclubroom.membership.application;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.membership.api.ClubAuthorization;
import pt.vclubroom.membership.api.ClubPermission;
import pt.vclubroom.membership.domain.ClubRole;
import pt.vclubroom.membership.domain.Membership;
import pt.vclubroom.membership.events.MemberRolesChanged;
import pt.vclubroom.membership.persistence.ClubRoleRepository;
import pt.vclubroom.membership.persistence.MembershipRepository;
import pt.vclubroom.shared.error.BusinessRuleException;
import pt.vclubroom.shared.error.ConflictException;
import pt.vclubroom.shared.error.ForbiddenException;
import pt.vclubroom.shared.error.NotFoundException;
import pt.vclubroom.shared.security.Actor;

/**
 * Custom roles (membership.md §9.6). ROLE_MANAGE in an active club for every change, and never beyond your own
 * permissions (BR-14): you cannot create, edit, delete or hand out a role that can do something you cannot. The Owner
 * role is immutable and never assignable here; system roles keep their names and cannot be deleted (BR-10).
 */
@Service
public class RoleService {

    private final ClubRoleRepository roles;
    private final MembershipRepository memberships;
    private final ClubAuthorization authorization;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public RoleService(ClubRoleRepository roles, MembershipRepository memberships, ClubAuthorization authorization,
                       ApplicationEventPublisher events, Clock clock) {
        this.roles = roles;
        this.memberships = memberships;
        this.authorization = authorization;
        this.events = events;
        this.clock = clock;
    }

    /** Members (and admins) see the club's roles, by position: Owner, custom roles, Member. */
    @Transactional(readOnly = true)
    public List<ClubRoleView> list(Actor actor, UUID clubId) {
        if (!actor.admin() && !memberships.existsActive(clubId, actor.userId())) {
            throw new ForbiddenException("MEMBERS_ONLY", "Only members can see the club's roles.");
        }
        return roles.findByClubIdOrderByPositionAsc(clubId).stream().map(r -> view(clubId, r)).toList();
    }

    @Transactional
    public ClubRoleView create(Actor actor, UUID clubId, String name, String color, Set<ClubPermission> permissions) {
        authorization.require(clubId, actor, ClubPermission.ROLE_MANAGE);
        ensureNoEscalation(actor, clubId, permissions);
        List<ClubRole> existing = roles.findByClubIdOrderByPositionAsc(clubId);
        ensureNameFree(existing, name.trim(), null);
        int position = existing.stream().filter(r -> !r.isMember()).mapToInt(ClubRole::position).max().orElse(0) + 1;
        return view(clubId, roles.save(ClubRole.custom(clubId, name.trim(), color, permissions, position, clock.instant())));
    }

    /** name/color: custom roles only (null keeps them); permissions: any role but Owner (null keeps them). */
    @Transactional
    public ClubRoleView update(Actor actor, UUID clubId, UUID roleId, String name, String color, Set<ClubPermission> permissions) {
        authorization.require(clubId, actor, ClubPermission.ROLE_MANAGE);
        ClubRole role = load(clubId, roleId);
        ensureNoEscalation(actor, clubId, role.effectivePermissions());          // not a role above you
        if (name != null && (!name.trim().equals(role.name()) || !Objects.equals(color, role.color()))) {
            ensureNameFree(roles.findByClubIdOrderByPositionAsc(clubId), name.trim(), roleId);
            role.rename(name.trim(), color);                                    // SYSTEM_ROLE_IMMUTABLE
        }
        if (permissions != null) {
            ensureNoEscalation(actor, clubId, permissions);
            role.changePermissions(permissions);                                // SYSTEM_ROLE_IMMUTABLE for Owner
        }
        return view(clubId, role);
    }

    /** Whoever held it simply loses it (membership_roles rows go with it). */
    @Transactional
    public void delete(Actor actor, UUID clubId, UUID roleId) {
        authorization.require(clubId, actor, ClubPermission.ROLE_MANAGE);
        ClubRole role = load(clubId, roleId);
        role.ensureDeletable();                                                 // SYSTEM_ROLE_IMMUTABLE
        ensureNoEscalation(actor, clubId, role.effectivePermissions());
        roles.delete(role);
    }

    /**
     * A member's custom roles become exactly roleIds; their Owner and Member roles stay as they are. Only roles you
     * add count against your permissions: taking one away is never an escalation.
     */
    @Transactional
    public Set<UUID> assign(Actor actor, UUID clubId, UUID userId, Set<UUID> roleIds) {
        authorization.require(clubId, actor, ClubPermission.ROLE_MANAGE);
        Membership member = memberships.findActive(clubId, userId)
            .orElseThrow(() -> new NotFoundException("MEMBER_NOT_FOUND", "This person is not a member of the club."));
        Map<UUID, ClubRole> clubRoles = roles.findByClubIdOrderByPositionAsc(clubId).stream()
            .collect(Collectors.toMap(ClubRole::id, Function.identity()));
        if (!roleIds.stream().allMatch(id -> clubRoles.containsKey(id) && !clubRoles.get(id).isSystem())) {
            throw new BusinessRuleException("ROLE_NOT_ASSIGNABLE", "Only the club's custom roles can be given here.");
        }
        Set<ClubPermission> added = EnumSet.noneOf(ClubPermission.class);
        roleIds.stream().filter(id -> !member.holds(id)).forEach(id -> added.addAll(clubRoles.get(id).effectivePermissions()));
        ensureNoEscalation(actor, clubId, added);

        Set<UUID> systemRoles = clubRoles.values().stream().filter(ClubRole::isSystem).map(ClubRole::id).collect(Collectors.toSet());
        member.replaceCustomRoles(systemRoles, roleIds);
        Instant now = clock.instant();
        events.publishEvent(new MemberRolesChanged(clubId, userId, new HashSet<>(member.roleIds()), actor.userId(), now));
        return member.roleIds();
    }

    /** BR-14: owners and admins hold every permission, so they are never stopped here. */
    private void ensureNoEscalation(Actor actor, UUID clubId, Set<ClubPermission> requested) {
        if (!authorization.permissionsOf(clubId, actor).containsAll(requested)) {
            throw new ForbiddenException("PERMISSION_ESCALATION", "You cannot grant permissions you do not have.");
        }
    }

    private static void ensureNameFree(List<ClubRole> existing, String name, UUID except) {
        if (existing.stream().anyMatch(r -> !r.id().equals(except) && r.name().equalsIgnoreCase(name))) {
            throw new ConflictException("ROLE_NAME_TAKEN", "A role with this name already exists.");
        }
    }

    private ClubRole load(UUID clubId, UUID roleId) {
        return roles.findById(roleId).filter(r -> r.clubId().equals(clubId))
            .orElseThrow(() -> new NotFoundException("ROLE_NOT_FOUND", "Role not found"));
    }

    private ClubRoleView view(UUID clubId, ClubRole r) {
        return new ClubRoleView(r.id(), r.name(), r.color(), r.isSystem(), r.isOwner(), r.effectivePermissions(),
            memberships.countActiveWithRole(clubId, r.id()));
    }
}
