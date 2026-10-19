package pt.vclubroom.membership.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.club.api.ClubCatalog;
import pt.vclubroom.membership.domain.ClubOwnership;
import pt.vclubroom.membership.domain.ClubRole;
import pt.vclubroom.membership.domain.Membership;
import pt.vclubroom.membership.domain.OwnershipInvitation;
import pt.vclubroom.membership.domain.SystemRole;
import pt.vclubroom.membership.events.OwnerAdded;
import pt.vclubroom.membership.events.OwnerRemoved;
import pt.vclubroom.membership.events.OwnerSteppedDown;
import pt.vclubroom.membership.events.OwnershipInvitationSent;
import pt.vclubroom.membership.persistence.ClubRoleRepository;
import pt.vclubroom.membership.persistence.MembershipRepository;
import pt.vclubroom.membership.persistence.OwnershipInvitationRepository;
import pt.vclubroom.shared.error.BusinessRuleException;
import pt.vclubroom.shared.error.ConflictException;
import pt.vclubroom.shared.error.ForbiddenException;
import pt.vclubroom.shared.error.NotFoundException;
import pt.vclubroom.shared.security.Actor;

/**
 * Co-ownership and ownership transfer (D-12, D-22, membership.md §9.5). Everything that may remove an owner first
 * locks the club's Owner role row (findSystemRoleForUpdate), so ownership changes in one club happen one after the
 * other: two owners stepping down at the same moment cannot leave the club without an owner (BR-04).
 */
@Service
public class OwnershipService {

    private final ClubCatalog clubs;
    private final MembershipRepository memberships;
    private final ClubRoleRepository roles;
    private final OwnershipInvitationRepository invitations;
    private final MembershipAdmission admission;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public OwnershipService(ClubCatalog clubs, MembershipRepository memberships, ClubRoleRepository roles,
                            OwnershipInvitationRepository invitations, MembershipAdmission admission,
                            ApplicationEventPublisher events, Clock clock) {
        this.clubs = clubs;
        this.memberships = memberships;
        this.roles = roles;
        this.invitations = invitations;
        this.admission = admission;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public UUID invite(Actor actor, UUID clubId, UUID inviteeId, boolean transfer) {
        requireActiveClub(clubId);
        ClubRole owner = ownerRole(clubId, false);
        requireOwner(clubId, actor.userId(), owner);
        Membership invitee = memberships.findActive(clubId, inviteeId)
            .orElseThrow(() -> new BusinessRuleException("INVITEE_NOT_MEMBER", "Only members can become owners."));
        if (invitee.holds(owner.id())) {
            throw new ConflictException("ALREADY_OWNER", "This member is already an owner.");
        }
        if (invitations.findPending(clubId, inviteeId).isPresent()) {                       // BR-06
            throw new ConflictException("INVITATION_PENDING", "An invitation is already pending.");
        }
        Instant now = clock.instant();
        OwnershipInvitation invitation = OwnershipInvitation.send(clubId, inviteeId, actor.userId(), transfer, now);
        invitations.save(invitation);
        events.publishEvent(new OwnershipInvitationSent(invitation.id(), clubId, inviteeId, actor.userId(),
            transfer, invitation.expiresAt(), now));
        return invitation.id();
    }

    @Transactional
    public void accept(Actor actor, UUID invitationId) {
        OwnershipInvitation invitation = loadForInvitee(actor, invitationId);
        UUID clubId = invitation.clubId();
        requireActiveClub(clubId);
        ClubRole owner = ownerRole(clubId, true);                                        // lock (BR-04)
        Instant now = clock.instant();

        invitation.accept(now);                                                          // PENDING + 7-day expiry (R-11)
        Membership invitee = memberships.findActive(clubId, actor.userId())
            .orElseThrow(() -> new BusinessRuleException("INVITEE_NOT_MEMBER", "You are no longer a member."));
        invitee.grantRole(owner.id());
        events.publishEvent(new OwnerAdded(clubId, actor.userId(), "INVITATION", invitation.invitedBy(), now));

        if (invitation.stepDownOnAccept()) {                                             // transfer (R-11)
            memberships.findActive(clubId, invitation.invitedBy())
                .filter(inviter -> inviter.holds(owner.id()))
                .ifPresent(inviter -> {
                    inviter.revokeRole(owner.id());
                    events.publishEvent(new OwnerSteppedDown(clubId, inviter.userId(), now));
                });
        }
    }

    @Transactional
    public void decline(Actor actor, UUID invitationId) {
        loadForInvitee(actor, invitationId).decline(clock.instant());
    }

    /** Any owner of the club may take back an invitation, not only the one who sent it. */
    @Transactional
    public void revoke(Actor actor, UUID invitationId) {
        OwnershipInvitation invitation = invitations.findById(invitationId)
            .orElseThrow(() -> new NotFoundException("INVITATION_NOT_FOUND", "Invitation not found"));
        requireOwner(invitation.clubId(), actor.userId(), ownerRole(invitation.clubId(), false));
        invitation.revoke(clock.instant());
    }

    @Transactional
    public void stepDown(Actor actor, UUID clubId) {
        requireActiveClub(clubId);
        ClubRole owner = ownerRole(clubId, true);                                        // lock (BR-04)
        Membership me = memberships.findActive(clubId, actor.userId())
            .filter(m -> m.holds(owner.id()))
            .orElseThrow(() -> new ForbiddenException("NOT_OWNER", "You are not an owner of this club."));
        ClubOwnership.ensureAnotherOwnerRemains(memberships.countActiveWithRole(clubId, owner.id()));   // LAST_OWNER
        me.revokeRole(owner.id());
        events.publishEvent(new OwnerSteppedDown(clubId, actor.userId(), clock.instant()));
    }

    /** D-22 rescue: an admin makes someone owner, admitting them first if they are not a member. Idempotent. */
    @Transactional
    public void assignOwner(Actor actor, UUID clubId, UUID userId) {
        requireAdmin(actor);
        requireActiveClub(clubId);
        ClubRole owner = ownerRole(clubId, true);
        Instant now = clock.instant();
        Membership membership = memberships.findActive(clubId, userId)
            .orElseGet(() -> admission.admit(clubId, userId, "ADMIN", now));
        if (!membership.holds(owner.id())) {
            membership.grantRole(owner.id());
            events.publishEvent(new OwnerAdded(clubId, userId, "ADMIN", actor.userId(), now));
        }
    }

    /** D-22: an admin removes an owner, never the last one. */
    @Transactional
    public void removeOwner(Actor actor, UUID clubId, UUID userId) {
        requireAdmin(actor);
        ClubRole owner = ownerRole(clubId, true);
        Membership membership = memberships.findActive(clubId, userId)
            .filter(m -> m.holds(owner.id()))
            .orElseThrow(() -> new NotFoundException("OWNER_NOT_FOUND", "This user is not an owner of the club."));
        ClubOwnership.ensureAnotherOwnerRemains(memberships.countActiveWithRole(clubId, owner.id()));
        membership.revokeRole(owner.id());
        events.publishEvent(new OwnerRemoved(clubId, userId, "ADMIN", actor.userId(), clock.instant()));
    }

    /** Someone else's invitation is "not found", like a missing one. */
    private OwnershipInvitation loadForInvitee(Actor actor, UUID invitationId) {
        return invitations.findById(invitationId)
            .filter(i -> i.inviteeId().equals(actor.userId()))
            .orElseThrow(() -> new NotFoundException("INVITATION_NOT_FOUND", "Invitation not found"));
    }

    private ClubRole ownerRole(UUID clubId, boolean forUpdate) {
        return (forUpdate ? roles.findSystemRoleForUpdate(clubId, SystemRole.OWNER)
                          : roles.findSystemRole(clubId, SystemRole.OWNER))
            .orElseThrow(() -> new NotFoundException("CLUB_NOT_FOUND", "Club not found"));
    }

    private void requireOwner(UUID clubId, UUID userId, ClubRole owner) {
        boolean isOwner = memberships.findActive(clubId, userId).map(m -> m.holds(owner.id())).orElse(false);
        if (!isOwner) {
            throw new ForbiddenException("NOT_OWNER", "Only owners can do this.");
        }
    }

    private void requireActiveClub(UUID clubId) {
        if (!clubs.require(clubId).isActive()) {
            throw new BusinessRuleException("CLUB_NOT_ACTIVE", "This club is read-only.");
        }
    }

    private static void requireAdmin(Actor actor) {
        if (!actor.admin()) {
            throw new ForbiddenException("ADMIN_ONLY", "Only admins can do this.");
        }
    }
}
