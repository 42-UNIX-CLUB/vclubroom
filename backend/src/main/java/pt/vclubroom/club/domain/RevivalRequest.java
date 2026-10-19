package pt.vclubroom.club.domain;

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

/** A student's request to bring an archived club back (D-14). An admin decides once; the decision is final. */
@Entity
@Table(name = "revival_requests", schema = "club")
public class RevivalRequest {

    @Id
    private UUID id;

    @Column(name = "club_id", nullable = false)
    private UUID clubId;

    @Column(name = "requester_id", nullable = false)
    private UUID requesterId;

    @Column(nullable = false)
    private String motivation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RevivalStatus status;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "review_note")
    private String reviewNote;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    private Long version;

    protected RevivalRequest() {
        // JPA
    }

    public static RevivalRequest submit(UUID clubId, UUID requesterId, String motivation, Instant now) {
        RevivalRequest r = new RevivalRequest();
        r.id = UUID.randomUUID();
        r.clubId = clubId;
        r.requesterId = requesterId;
        r.motivation = motivation;
        r.status = RevivalStatus.PENDING;
        r.createdAt = now.truncatedTo(ChronoUnit.MICROS);
        return r;
    }

    public void approve(UUID adminId, Instant now) {
        decide(RevivalStatus.APPROVED, adminId, null, now);
    }

    public void reject(UUID adminId, String note, Instant now) {
        decide(RevivalStatus.REJECTED, adminId, note, now);
    }

    private void decide(RevivalStatus outcome, UUID adminId, String note, Instant now) {
        if (status != RevivalStatus.PENDING) {
            throw new BusinessRuleException("REVIVAL_NOT_PENDING", "This revival request was already decided.");
        }
        this.status = outcome;
        this.reviewedBy = adminId;
        this.reviewNote = note;
        this.reviewedAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    public UUID id() { return id; }
    public UUID clubId() { return clubId; }
    public UUID requesterId() { return requesterId; }
    public String motivation() { return motivation; }
    public RevivalStatus status() { return status; }
    public String reviewNote() { return reviewNote; }
    public Instant createdAt() { return createdAt; }
}
