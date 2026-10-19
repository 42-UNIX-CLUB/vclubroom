package pt.vclubroom.membership.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.membership.api.ClubAuthorization;
import pt.vclubroom.membership.api.ClubPermission;
import pt.vclubroom.membership.domain.ClubOwnership;
import pt.vclubroom.membership.domain.ClubRole;
import pt.vclubroom.membership.domain.Membership;
import pt.vclubroom.membership.domain.SystemRole;
import pt.vclubroom.membership.events.MemberLeft;
import pt.vclubroom.membership.persistence.ClubRoleRepository;
import pt.vclubroom.membership.persistence.MembershipRepository;
import pt.vclubroom.shared.error.BusinessRuleException;
import pt.vclubroom.shared.error.ForbiddenException;
import pt.vclubroom.shared.error.NotFoundException;
import pt.vclubroom.shared.security.Actor;

/** Ending memberships: leaving, and being removed. */
@Service
public class MemberService {

    private final MembershipRepository memberships;
    private final ClubRoleRepository roles;
    private final ClubAuthorization authorization;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public MemberService(MembershipRepository memberships, ClubRoleRepository roles, ClubAuthorization authorization,
                         ApplicationEventPublisher events, Clock clock) {
        this.memberships = memberships;
        this.roles = roles;
        this.authorization = authorization;
        this.events = events;
        this.clock = clock;
    }

    /** The membership ends (its history stays). The last owner cannot leave (BR-04): invite a co-owner first. */
    @Transactional
    public void leave(Actor actor, UUID clubId) {
        Optional<ClubRole> owner = roles.findSystemRoleForUpdate(clubId, SystemRole.OWNER);   // lock (BR-04)
        Membership me = memberships.findActive(clubId, actor.userId())
            .orElseThrow(() -> new BusinessRuleException("NOT_A_MEMBER", "You are not a member of this club."));
        owner.filter(role -> me.holds(role.id()))
            .ifPresent(role -> ClubOwnership.ensureAnotherOwnerRemains(memberships.countActiveWithRole(clubId, role.id())));

        Instant now = clock.instant();
        me.leave(now);
        events.publishEvent(new MemberLeft(clubId, actor.userId(), me.id(), "LEFT", actor.userId(), now));
    }

    /**
     * MEMBER_REMOVE ends someone else's membership (their history stays). Owners are never removed this way (BR-05):
     * an admin first takes the ownership away (D-22). The Owner role row is locked, as for every ownership change,
     * so the member cannot become an owner in between.
     */
    @Transactional
    public void remove(Actor actor, UUID clubId, UUID userId) {
        authorization.require(clubId, actor, ClubPermission.MEMBER_REMOVE);   // CLUB_NOT_ACTIVE / PERMISSION_DENIED
        Optional<ClubRole> owner = roles.findSystemRoleForUpdate(clubId, SystemRole.OWNER);
        Membership member = memberships.findActive(clubId, userId)
            .orElseThrow(() -> new NotFoundException("MEMBER_NOT_FOUND", "This person is not a member of the club."));
        if (owner.filter(role -> member.holds(role.id())).isPresent()) {
            throw new ForbiddenException("CANNOT_REMOVE_OWNER", "Owners cannot be removed. An admin can take their ownership away first.");
        }
        Instant now = clock.instant();
        member.remove(actor.userId(), now);
        events.publishEvent(new MemberLeft(clubId, userId, member.id(), "REMOVED", actor.userId(), now));
    }
}
