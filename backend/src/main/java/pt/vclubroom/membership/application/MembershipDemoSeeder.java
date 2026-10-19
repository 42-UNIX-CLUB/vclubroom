package pt.vclubroom.membership.application;

import java.util.List;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import pt.vclubroom.club.api.ClubCatalog;
import pt.vclubroom.identity.api.UserDirectory;
import pt.vclubroom.membership.domain.SystemRole;
import pt.vclubroom.membership.persistence.ClubRoleRepository;
import pt.vclubroom.membership.persistence.JoinRequestRepository;
import pt.vclubroom.membership.persistence.MembershipRepository;
import pt.vclubroom.shared.demo.DemoData;
import pt.vclubroom.shared.security.Actor;

/**
 * Members join like students do: an OPEN club admits at once; an APPROVAL club gets a request, which its owner
 * approves, except one left waiting in 42 Robotics so the queue has something to show.
 */
@Component
@Profile(DemoData.PROFILE)
@Order(DemoData.MEMBERS)
class MembershipDemoSeeder implements ApplicationRunner {

    /** approver = null: an OPEN club, or a request left pending. */
    private record Join(String club, String member, String approver) { }

    private static final List<Join> JOINS = List.of(
        new Join("chess-club", "carol", null),
        new Join("chess-club", "dave", null),
        new Join("photo-club", "bob", "dave"),
        new Join("hiking-club", "carol", "bob"),
        new Join("game-dev-club", "bob", "carol"),
        new Join(DemoData.FILM_SOCIETY, "carol", "dave"),
        new Join("42-robotics", "dave", null));

    private final JoinService joinService;
    private final ClubCatalog clubs;
    private final UserDirectory users;
    private final ClubRoleRepository roles;
    private final MembershipRepository memberships;
    private final JoinRequestRepository joinRequests;

    MembershipDemoSeeder(JoinService joinService, ClubCatalog clubs, UserDirectory users, ClubRoleRepository roles,
                         MembershipRepository memberships, JoinRequestRepository joinRequests) {
        this.joinService = joinService;
        this.clubs = clubs;
        this.users = users;
        this.roles = roles;
        this.memberships = memberships;
        this.joinRequests = joinRequests;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (Join join : JOINS) {
            UUID clubId = clubs.findBySlug(join.club()).orElseThrow().id();
            DemoData.awaitUntil(() -> roles.findSystemRole(clubId, SystemRole.OWNER).isPresent()
                && memberships.countActive(clubId) > 0, join.club() + "'s owner");          // ClubApproved, handled here
            UUID member = userId(join.member());
            if (memberships.existsActive(clubId, member) || joinRequests.existsPending(clubId, member)) {
                continue;
            }
            JoinOutcome outcome = joinService.join(new Actor(member, false), clubId, "Hi! Can I join?");
            if (outcome.status() == JoinOutcome.Status.REQUESTED && join.approver() != null) {
                joinService.approve(new Actor(userId(join.approver()), false), clubId, outcome.requestId());
            }
        }
    }

    private UUID userId(String login) {
        return users.findByLogin(login).orElseThrow().id();
    }
}
