package pt.vclubroom.membership.application;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.club.api.ClubCatalog;
import pt.vclubroom.club.api.ClubSnapshot;
import pt.vclubroom.identity.api.UserDirectory;
import pt.vclubroom.identity.api.UserSummary;
import pt.vclubroom.membership.api.ClubAuthorization;
import pt.vclubroom.membership.domain.ClubRole;
import pt.vclubroom.membership.domain.Membership;
import pt.vclubroom.membership.domain.SystemRole;
import pt.vclubroom.membership.persistence.ClubRoleRepository;
import pt.vclubroom.membership.persistence.JoinRequestRepository;
import pt.vclubroom.membership.persistence.MembershipRepository;
import pt.vclubroom.membership.persistence.OwnershipInvitationRepository;
import pt.vclubroom.shared.security.Actor;

/** Read side of memberships. E07-S04 adds the member list (members only). */
@Service
@Transactional(readOnly = true)
public class MemberQueryService {

    private final MembershipRepository memberships;
    private final ClubRoleRepository roles;
    private final JoinRequestRepository joinRequests;
    private final OwnershipInvitationRepository invitations;
    private final ClubAuthorization authorization;
    private final ClubCatalog clubs;
    private final UserDirectory users;

    public MemberQueryService(MembershipRepository memberships, ClubRoleRepository roles,
                              JoinRequestRepository joinRequests, OwnershipInvitationRepository invitations,
                              ClubAuthorization authorization, ClubCatalog clubs, UserDirectory users) {
        this.memberships = memberships;
        this.roles = roles;
        this.joinRequests = joinRequests;
        this.invitations = invitations;
        this.authorization = authorization;
        this.clubs = clubs;
        this.users = users;
    }

    /** Public facts of the club page. A club that does not exist (or has no members yet) gives 0 and no owners. */
    public MemberSummary summary(UUID clubId) {
        List<UUID> ownerIds = roles.findSystemRole(clubId, SystemRole.OWNER)
            .map(owner -> memberships.findActiveWithRole(clubId, owner.id()).stream().map(Membership::userId).toList())
            .orElse(List.of());
        List<UserSummary> owners = users.findByIds(ownerIds).values().stream()
            .sorted(Comparator.comparing(UserSummary::displayName, String.CASE_INSENSITIVE_ORDER))
            .toList();
        return new MemberSummary(memberships.countActive(clubId), owners);
    }

    /** Admins get every permission without being members (R-08), so `member` and `permissions` are separate facts. */
    public MembershipView membershipOf(UUID clubId, Actor actor) {
        List<ClubRole> held = roles.findHeldRoles(clubId, actor.userId());
        return new MembershipView(
            memberships.existsActive(clubId, actor.userId()),
            held.stream().anyMatch(ClubRole::isOwner),
            held.stream().sorted(Comparator.comparingInt(ClubRole::position)).map(MemberQueryService::view).toList(),
            authorization.permissionsOf(clubId, actor),
            joinRequests.findPending(clubId, actor.userId())
                .map(r -> new MembershipView.PendingRequest(r.id(), r.createdAt())).orElse(null),
            invitations.findPending(clubId, actor.userId())
                .map(i -> new MembershipView.PendingInvitation(i.id(), i.stepDownOnAccept(), i.expiresAt())).orElse(null));
    }

    /** My clubs, by name, with my roles in each. */
    public List<MyClubMembership> myMemberships(Actor actor) {
        List<Membership> mine = memberships.findActiveOfUser(actor.userId());
        Map<UUID, ClubSnapshot> clubById = clubs.findByIds(mine.stream().map(Membership::clubId).toList());
        Map<UUID, ClubRole> roleById = byId(roles.findAllById(mine.stream().flatMap(m -> m.roleIds().stream()).toList()));
        return mine.stream()
            .filter(m -> clubById.containsKey(m.clubId()))
            .map(m -> {
                ClubSnapshot club = clubById.get(m.clubId());
                List<ClubRole> held = m.roleIds().stream().map(roleById::get)
                    .sorted(Comparator.comparingInt(ClubRole::position)).toList();
                return new MyClubMembership(club.id(), club.slug(), club.name(), club.status(),
                    held.stream().anyMatch(ClubRole::isOwner), held.stream().map(MemberQueryService::view).toList(),
                    m.joinedAt());
            })
            .sorted(Comparator.comparing(MyClubMembership::name, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    private static Map<UUID, ClubRole> byId(Collection<ClubRole> list) {
        return list.stream().collect(Collectors.toMap(ClubRole::id, Function.identity()));
    }

    private static MembershipView.RoleView view(ClubRole r) {
        return new MembershipView.RoleView(r.id(), r.name(), r.color(), r.isSystem());
    }
}
