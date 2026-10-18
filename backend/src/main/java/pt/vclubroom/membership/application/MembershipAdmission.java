package pt.vclubroom.membership.application;

import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import pt.vclubroom.membership.domain.ClubRole;
import pt.vclubroom.membership.domain.Membership;
import pt.vclubroom.membership.domain.SystemRole;
import pt.vclubroom.membership.events.MemberJoined;
import pt.vclubroom.membership.persistence.ClubRoleRepository;
import pt.vclubroom.membership.persistence.MembershipRepository;

/** The one place where memberships are created (join, approval, founding, revival, admin rescue). */
@Component
class MembershipAdmission {

    private final MembershipRepository memberships;
    private final ClubRoleRepository roles;
    private final ApplicationEventPublisher events;

    MembershipAdmission(MembershipRepository memberships, ClubRoleRepository roles, ApplicationEventPublisher events) {
        this.memberships = memberships;
        this.roles = roles;
        this.events = events;
    }

    Membership admit(UUID clubId, UUID userId, String via, Instant now) {
        ClubRole memberRole = roles.findSystemRole(clubId, SystemRole.MEMBER)
            .orElseThrow(() -> new IllegalStateException("Club " + clubId + " has no Member role"));
        Membership membership = Membership.start(clubId, userId, memberRole.id(), now);
        memberships.save(membership);
        events.publishEvent(new MemberJoined(clubId, userId, membership.id(), via, now));
        return membership;
    }
}
