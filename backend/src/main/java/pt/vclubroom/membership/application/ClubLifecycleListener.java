package pt.vclubroom.membership.application;

import java.time.Clock;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import pt.vclubroom.club.api.ClubStatus;
import pt.vclubroom.club.api.JoinPolicy;
import pt.vclubroom.club.events.ClubApproved;
import pt.vclubroom.club.events.ClubArchived;
import pt.vclubroom.club.events.ClubProfileUpdated;
import pt.vclubroom.club.events.ClubRevived;
import pt.vclubroom.membership.domain.ClubRole;
import pt.vclubroom.membership.domain.Membership;
import pt.vclubroom.membership.domain.SystemRole;
import pt.vclubroom.membership.events.JoinRequestDecided;
import pt.vclubroom.membership.events.OwnerAdded;
import pt.vclubroom.membership.events.OwnerRemoved;
import pt.vclubroom.membership.persistence.ClubRoleRepository;
import pt.vclubroom.membership.persistence.JoinRequestRepository;
import pt.vclubroom.membership.persistence.MembershipRepository;
import pt.vclubroom.membership.persistence.OwnershipInvitationRepository;

/**
 * Reacts to the club lifecycle. Each handler runs asynchronously after the club transaction committed,
 * in its own transaction, and is IDEMPOTENT (events are delivered at least once).
 */
@Component
class ClubLifecycleListener {

    private final ClubRoleRepository roles;
    private final MembershipRepository memberships;
    private final JoinRequestRepository joinRequests;
    private final OwnershipInvitationRepository invitations;
    private final MembershipAdmission admission;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    ClubLifecycleListener(ClubRoleRepository roles, MembershipRepository memberships, JoinRequestRepository joinRequests,
                          OwnershipInvitationRepository invitations, MembershipAdmission admission,
                          ApplicationEventPublisher events, Clock clock) {
        this.roles = roles;
        this.memberships = memberships;
        this.joinRequests = joinRequests;
        this.invitations = invitations;
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

    /** R-10: nobody can join or take over an archived club, so what was waiting is closed (bulk, idempotent). */
    @ApplicationModuleListener
    void on(ClubArchived event) {
        Instant now = clock.instant();
        joinRequests.cancelAllPending(event.clubId(), now);
        invitations.expireAllPending(event.clubId(), now);
    }

    /**
     * R-05: the requester becomes the owner (joining first if needed), and the previous owners stay as members, with
     * their history. A second delivery finds the requester already the only owner and changes nothing.
     */
    @ApplicationModuleListener
    void on(ClubRevived event) {
        Instant now = clock.instant();
        ClubRole owner = roles.findSystemRoleForUpdate(event.clubId(), SystemRole.OWNER)
            .orElseThrow(() -> new IllegalStateException("Owner role missing for " + event.clubId()));

        memberships.findActiveWithRole(event.clubId(), owner.id()).stream()
            .filter(m -> !m.userId().equals(event.newOwnerId()))
            .forEach(m -> {
                m.revokeRole(owner.id());
                events.publishEvent(new OwnerRemoved(event.clubId(), m.userId(), "REVIVAL", event.approvedBy(), now));
            });

        Membership requester = memberships.findActive(event.clubId(), event.newOwnerId())
            .orElseGet(() -> admission.admit(event.clubId(), event.newOwnerId(), "REVIVAL", now));
        if (!requester.holds(owner.id())) {
            requester.grantRole(owner.id());
            events.publishEvent(new OwnerAdded(event.clubId(), event.newOwnerId(), "REVIVAL", event.approvedBy(), now));
        }
    }

    /** A club switched to OPEN: everyone waiting is in (a second delivery finds no pending request left). */
    @ApplicationModuleListener
    void on(ClubProfileUpdated event) {
        if (event.status() != ClubStatus.ACTIVE || event.joinPolicy() != JoinPolicy.OPEN) {
            return;
        }
        Instant now = clock.instant();
        joinRequests.findAllPending(event.clubId()).forEach(request -> {
            request.approve(null, now);                                    // system decision
            if (!memberships.existsActive(event.clubId(), request.userId())) {
                admission.admit(event.clubId(), request.userId(), "APPROVED_REQUEST", now);
            }
            events.publishEvent(new JoinRequestDecided(request.id(), event.clubId(), request.userId(), true, null, now));
        });
    }
}
