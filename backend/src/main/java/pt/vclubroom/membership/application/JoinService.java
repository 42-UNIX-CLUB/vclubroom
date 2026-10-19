package pt.vclubroom.membership.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.club.api.ClubCatalog;
import pt.vclubroom.club.api.ClubSnapshot;
import pt.vclubroom.club.api.JoinPolicy;
import pt.vclubroom.membership.api.ClubAuthorization;
import pt.vclubroom.membership.api.ClubPermission;
import pt.vclubroom.membership.domain.JoinRequest;
import pt.vclubroom.membership.domain.Membership;
import pt.vclubroom.membership.events.JoinRequestDecided;
import pt.vclubroom.membership.events.JoinRequested;
import pt.vclubroom.membership.persistence.JoinRequestRepository;
import pt.vclubroom.membership.persistence.MembershipRepository;
import pt.vclubroom.shared.error.BusinessRuleException;
import pt.vclubroom.shared.error.ConflictException;
import pt.vclubroom.shared.error.NotFoundException;
import pt.vclubroom.shared.security.Actor;

/** Joining (D-10): OPEN clubs admit at once, APPROVAL clubs (the default) get a request that MEMBER_APPROVE decides. */
@Service
public class JoinService {

    private final ClubCatalog clubs;
    private final MembershipRepository memberships;
    private final JoinRequestRepository joinRequests;
    private final MembershipAdmission admission;
    private final ClubAuthorization authorization;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public JoinService(ClubCatalog clubs, MembershipRepository memberships, JoinRequestRepository joinRequests,
                       MembershipAdmission admission, ClubAuthorization authorization,
                       ApplicationEventPublisher events, Clock clock) {
        this.clubs = clubs;
        this.memberships = memberships;
        this.joinRequests = joinRequests;
        this.admission = admission;
        this.authorization = authorization;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public JoinOutcome join(Actor actor, UUID clubId, String message) {
        ClubSnapshot club = clubs.require(clubId);
        if (!club.isActive()) {
            throw new BusinessRuleException("CLUB_NOT_ACTIVE", "This club is not accepting members.");
        }
        if (memberships.existsActive(clubId, actor.userId())) {
            throw new ConflictException("ALREADY_MEMBER", "You are already a member of this club.");
        }
        Instant now = clock.instant();

        if (club.joinPolicy() == JoinPolicy.OPEN) {                              // BR-08
            Membership membership = admission.admit(clubId, actor.userId(), "OPEN_JOIN", now);
            return JoinOutcome.joined(membership.id());
        }
        if (joinRequests.existsPending(clubId, actor.userId())) {               // BR-07
            throw new ConflictException("JOIN_ALREADY_REQUESTED", "Your request is waiting for approval.");
        }
        JoinRequest request = JoinRequest.submit(clubId, actor.userId(), message, now);
        joinRequests.save(request);
        events.publishEvent(new JoinRequested(request.id(), clubId, actor.userId(), now));
        return JoinOutcome.requested(request.id());
    }

    @Transactional
    public void approve(Actor actor, UUID clubId, UUID requestId) {
        authorization.require(clubId, actor, ClubPermission.MEMBER_APPROVE);
        JoinRequest request = load(clubId, requestId);
        Instant now = clock.instant();
        request.approve(actor.userId(), now);                                    // JOIN_REQUEST_NOT_PENDING if decided
        if (!memberships.existsActive(clubId, request.userId())) {
            admission.admit(clubId, request.userId(), "APPROVED_REQUEST", now);
        }
        events.publishEvent(new JoinRequestDecided(request.id(), clubId, request.userId(), true, actor.userId(), now));
    }

    @Transactional
    public void reject(Actor actor, UUID clubId, UUID requestId) {
        authorization.require(clubId, actor, ClubPermission.MEMBER_APPROVE);
        JoinRequest request = load(clubId, requestId);
        Instant now = clock.instant();
        request.reject(actor.userId(), now);
        events.publishEvent(new JoinRequestDecided(request.id(), clubId, request.userId(), false, actor.userId(), now));
    }

    /** The requester changed their mind. Not a decision, so nobody is notified. */
    @Transactional
    public void cancelMine(Actor actor, UUID clubId) {
        JoinRequest request = joinRequests.findPending(clubId, actor.userId())
            .orElseThrow(() -> new NotFoundException("JOIN_REQUEST_NOT_FOUND", "You have no pending request for this club."));
        request.cancel(clock.instant());
    }

    /** A request ID from another club is "not found", like a missing one. */
    private JoinRequest load(UUID clubId, UUID requestId) {
        return joinRequests.findById(requestId)
            .filter(r -> r.clubId().equals(clubId))
            .orElseThrow(() -> new NotFoundException("JOIN_REQUEST_NOT_FOUND", "Join request not found"));
    }
}
