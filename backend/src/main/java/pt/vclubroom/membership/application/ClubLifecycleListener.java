package pt.vclubroom.membership.application;

import java.time.Clock;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import pt.vclubroom.club.events.ClubApproved;
import pt.vclubroom.membership.domain.ClubRole;
import pt.vclubroom.membership.domain.Membership;
import pt.vclubroom.membership.domain.SystemRole;
import pt.vclubroom.membership.events.OwnerAdded;
import pt.vclubroom.membership.persistence.ClubRoleRepository;
import pt.vclubroom.membership.persistence.MembershipRepository;

/**
 * Reacts to the club lifecycle. Each handler runs asynchronously after the club transaction committed,
 * in its own transaction, and is IDEMPOTENT (events are delivered at least once).
 * Later stories add handlers for ClubProfileUpdated (E07-S02), ClubArchived and ClubRevived (E14).
 */
@Component
class ClubLifecycleListener {

    private final ClubRoleRepository roles;
    private final MembershipRepository memberships;
    private final MembershipAdmission admission;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    ClubLifecycleListener(ClubRoleRepository roles, MembershipRepository memberships, MembershipAdmission admission,
                          ApplicationEventPublisher events, Clock clock) {
        this.roles = roles;
        this.memberships = memberships;
        this.admission = admission;
        this.events = events;
        this.clock = clock;
    }

    @ApplicationModuleListener
    void on(ClubApproved event) {
        Instant now = clock.instant();
        ClubRole owner = roles.findSystemRole(event.clubId(), SystemRole.OWNER)
            .orElseGet(() -> roles.save(ClubRole.systemOwner(event.clubId(), now)));
        roles.findSystemRole(event.clubId(), SystemRole.MEMBER)
            .orElseGet(() -> roles.save(ClubRole.systemMember(event.clubId(), now)));

        if (memberships.existsActive(event.clubId(), event.founderId())) {
            return;                                                        // already processed (idempotency)
        }
        Membership founder = admission.admit(event.clubId(), event.founderId(), "FOUNDED", now);
        founder.grantRole(owner.id());
        events.publishEvent(new OwnerAdded(event.clubId(), event.founderId(), "FOUNDER", event.approvedBy(), now));
    }
}
