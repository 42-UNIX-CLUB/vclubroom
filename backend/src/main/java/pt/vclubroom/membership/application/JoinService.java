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
import pt.vclubroom.membership.domain.JoinRequest;
import pt.vclubroom.membership.domain.Membership;
import pt.vclubroom.membership.events.JoinRequested;
import pt.vclubroom.membership.persistence.JoinRequestRepository;
import pt.vclubroom.membership.persistence.MembershipRepository;
import pt.vclubroom.shared.error.BusinessRuleException;
import pt.vclubroom.shared.error.ConflictException;
import pt.vclubroom.shared.security.Actor;

/** Joining (D-10): OPEN clubs admit at once, APPROVAL clubs (the default) get a request. E07-S03 adds approve/reject. */
@Service
public class JoinService {

    private final ClubCatalog clubs;
    private final MembershipRepository memberships;
    private final JoinRequestRepository joinRequests;
    private final MembershipAdmission admission;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public JoinService(ClubCatalog clubs, MembershipRepository memberships, JoinRequestRepository joinRequests,
                       MembershipAdmission admission, ApplicationEventPublisher events, Clock clock) {
        this.clubs = clubs;
        this.memberships = memberships;
        this.joinRequests = joinRequests;
        this.admission = admission;
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
}
