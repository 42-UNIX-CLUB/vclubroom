package pt.vclubroom.membership.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import pt.vclubroom.shared.error.BusinessRuleException;

/** A request to join an APPROVAL club: PENDING → APPROVED | REJECTED | CANCELLED (used from E07-S02). */
@Entity
@Table(name = "join_requests", schema = "membership")
public class JoinRequest {

    @Id
    private UUID id;

    @Column(name = "club_id", nullable = false)
    private UUID clubId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JoinRequestStatus status;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    private Long version;

    protected JoinRequest() {
        // JPA
    }

    public static JoinRequest submit(UUID clubId, UUID userId, String message, Instant now) {
        JoinRequest r = new JoinRequest();
        r.id = UUID.randomUUID();
        r.clubId = clubId;
        r.userId = userId;
        r.message = message;
        r.status = JoinRequestStatus.PENDING;
        r.createdAt = now.truncatedTo(ChronoUnit.MICROS);
        return r;
    }

    /** decidedBy is null when the system decides (switch to OPEN auto-approves pending requests). */
    public void approve(UUID decidedBy, Instant now) {
        decide(JoinRequestStatus.APPROVED, decidedBy, now);
    }

    public void reject(UUID decidedBy, Instant now) {
        decide(JoinRequestStatus.REJECTED, decidedBy, now);
    }

    public void cancel(Instant now) {
        decide(JoinRequestStatus.CANCELLED, userId, now);
    }

    private void decide(JoinRequestStatus outcome, UUID by, Instant now) {
        if (status != JoinRequestStatus.PENDING) {
            throw new BusinessRuleException("JOIN_REQUEST_NOT_PENDING", "This join request was already decided.");
        }
        this.status = outcome;
        this.decidedBy = by;
        this.decidedAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    public UUID id() { return id; }
    public UUID clubId() { return clubId; }
    public UUID userId() { return userId; }
    public String message() { return message; }
    public JoinRequestStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
}
