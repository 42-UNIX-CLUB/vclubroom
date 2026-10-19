package pt.vclubroom.membership.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import pt.vclubroom.shared.error.BusinessRuleException;

/**
 * An owner invites someone to co-own the club (D-12); with stepDownOnAccept it is an ownership transfer.
 * PENDING → ACCEPTED | DECLINED | REVOKED | EXPIRED, valid for 7 days (used from E13-S01).
 */
@Entity
@Table(name = "ownership_invitations", schema = "membership")
public class OwnershipInvitation {

    public static final Duration VALIDITY = Duration.ofDays(7);

    @Id
    private UUID id;

    @Column(name = "club_id", nullable = false)
    private UUID clubId;

    @Column(name = "invitee_id", nullable = false)
    private UUID inviteeId;

    @Column(name = "invited_by", nullable = false)
    private UUID invitedBy;

    @Column(name = "step_down_on_accept", nullable = false)
    private boolean stepDownOnAccept;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvitationStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Version
    private Long version;

    protected OwnershipInvitation() {
        // JPA
    }

    public static OwnershipInvitation send(UUID clubId, UUID inviteeId, UUID invitedBy, boolean stepDownOnAccept,
                                           Instant now) {
        OwnershipInvitation i = new OwnershipInvitation();
        i.id = UUID.randomUUID();
        i.clubId = clubId;
        i.inviteeId = inviteeId;
        i.invitedBy = invitedBy;
        i.stepDownOnAccept = stepDownOnAccept;
        i.status = InvitationStatus.PENDING;
        i.createdAt = now.truncatedTo(ChronoUnit.MICROS);
        i.expiresAt = i.createdAt.plus(VALIDITY);
        return i;
    }

    public void accept(Instant now) {
        if (status == InvitationStatus.PENDING && isExpired(now)) {
            throw new BusinessRuleException("INVITATION_EXPIRED", "This invitation has expired.");
        }
        respond(InvitationStatus.ACCEPTED, now);
    }

    public void decline(Instant now) {
        respond(InvitationStatus.DECLINED, now);
    }

    public void revoke(Instant now) {
        respond(InvitationStatus.REVOKED, now);
    }

    public void expire(Instant now) {
        respond(InvitationStatus.EXPIRED, now);
    }

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    private void respond(InvitationStatus outcome, Instant now) {
        if (status != InvitationStatus.PENDING) {
            throw new BusinessRuleException("INVITATION_NOT_PENDING", "This invitation was already answered.");
        }
        this.status = outcome;
        this.respondedAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    public UUID id() { return id; }
    public UUID clubId() { return clubId; }
    public UUID inviteeId() { return inviteeId; }
    public UUID invitedBy() { return invitedBy; }
    public boolean stepDownOnAccept() { return stepDownOnAccept; }
    public InvitationStatus status() { return status; }
    public Instant expiresAt() { return expiresAt; }
    public Instant createdAt() { return createdAt; }
}
