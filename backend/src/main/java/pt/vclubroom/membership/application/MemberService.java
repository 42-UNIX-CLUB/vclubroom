package pt.vclubroom.membership.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.membership.domain.ClubOwnership;
import pt.vclubroom.membership.domain.ClubRole;
import pt.vclubroom.membership.domain.Membership;
import pt.vclubroom.membership.domain.SystemRole;
import pt.vclubroom.membership.events.MemberLeft;
import pt.vclubroom.membership.persistence.ClubRoleRepository;
import pt.vclubroom.membership.persistence.MembershipRepository;
import pt.vclubroom.shared.error.BusinessRuleException;
import pt.vclubroom.shared.security.Actor;

/** Ending memberships. E07-S04 adds removing a member (MEMBER_REMOVE). */
@Service
public class MemberService {

    private final MembershipRepository memberships;
    private final ClubRoleRepository roles;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public MemberService(MembershipRepository memberships, ClubRoleRepository roles, ApplicationEventPublisher events,
                         Clock clock) {
        this.memberships = memberships;
        this.roles = roles;
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
}
