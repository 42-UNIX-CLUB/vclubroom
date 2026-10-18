package pt.vclubroom.membership.application;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.identity.api.UserDirectory;
import pt.vclubroom.identity.api.UserSummary;
import pt.vclubroom.membership.domain.Membership;
import pt.vclubroom.membership.domain.SystemRole;
import pt.vclubroom.membership.persistence.ClubRoleRepository;
import pt.vclubroom.membership.persistence.MembershipRepository;

/** Read side of memberships. E07-S04 adds the member list (members only). */
@Service
@Transactional(readOnly = true)
public class MemberQueryService {

    private final MembershipRepository memberships;
    private final ClubRoleRepository roles;
    private final UserDirectory users;

    public MemberQueryService(MembershipRepository memberships, ClubRoleRepository roles, UserDirectory users) {
        this.memberships = memberships;
        this.roles = roles;
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
}
