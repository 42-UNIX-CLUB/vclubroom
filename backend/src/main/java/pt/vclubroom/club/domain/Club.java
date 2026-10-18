package pt.vclubroom.club.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import pt.vclubroom.club.api.ClubStatus;
import pt.vclubroom.club.api.JoinPolicy;
import pt.vclubroom.shared.error.BusinessRuleException;

/** The club aggregate: profile, join policy, lifecycle status and eras. */
@Entity
@Table(name = "clubs", schema = "club")
public class Club {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String name;

    private String tagline;

    private String description;

    @Column(nullable = false)
    private String category;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "tags", nullable = false, columnDefinition = "text[]")
    private List<String> tags = new ArrayList<>();

    @Column(name = "avatar_media_id")
    private UUID avatarMediaId;

    @Column(name = "banner_media_id")
    private UUID bannerMediaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClubStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "join_policy", nullable = false)
    private JoinPolicy joinPolicy;

    @Column(name = "founder_id", nullable = false)
    private UUID founderId;

    @Column(name = "current_era", nullable = false)
    private int currentEra;

    @Column(name = "review_note")
    private String reviewNote;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "club_eras", schema = "club", joinColumns = @JoinColumn(name = "club_id"))
    @OrderBy("number ASC")
    private List<ClubEra> eras = new ArrayList<>();

    @Version
    private Long version;   // null until the first save, so Spring Data persists (not merges) new clubs

    protected Club() {
        // JPA
    }

    public static Club requestCreation(Slug slug, String name, String tagline, String description,
                                       String category, List<String> tags, UUID founderId, Instant now) {
        Club c = new Club();
        c.id = UUID.randomUUID();
        c.slug = slug.value();
        c.name = name.trim();
        c.tagline = tagline;
        c.description = description;
        c.category = category;
        c.tags = new ArrayList<>(tags == null ? List.of() : tags);
        c.founderId = Objects.requireNonNull(founderId);
        c.status = ClubStatus.PENDING_APPROVAL;
        c.joinPolicy = JoinPolicy.APPROVAL;                   // D-10 default
        c.currentEra = 0;                                     // no era until approved
        c.createdAt = now.truncatedTo(ChronoUnit.MICROS);
        c.updatedAt = c.createdAt;
        return c;
    }

    public void approve(UUID adminId, Instant now) {
        requireStatus(ClubStatus.PENDING_APPROVAL, "CLUB_NOT_PENDING");
        Instant at = now.truncatedTo(ChronoUnit.MICROS);
        this.status = ClubStatus.ACTIVE;
        this.currentEra = 1;
        this.eras.add(ClubEra.start(1, EraStartReason.FOUNDED, at));
        review(adminId, null, at);
    }

    public void reject(UUID adminId, String reason, Instant now) {
        requireStatus(ClubStatus.PENDING_APPROVAL, "CLUB_NOT_PENDING");
        this.status = ClubStatus.REJECTED;                     // final (BR-18)
        review(adminId, reason, now.truncatedTo(ChronoUnit.MICROS));
    }

    public void archive(UUID adminId, String reason, Instant now) {
        requireStatus(ClubStatus.ACTIVE, "CLUB_NOT_ACTIVE");
        Instant at = now.truncatedTo(ChronoUnit.MICROS);
        this.status = ClubStatus.ARCHIVED;
        currentEraRecord().end(at, reason);
        review(adminId, reason, at);
    }

    public void revive(Instant now) {
        requireStatus(ClubStatus.ARCHIVED, "CLUB_NOT_ARCHIVED");
        Instant at = now.truncatedTo(ChronoUnit.MICROS);
        this.status = ClubStatus.ACTIVE;
        this.currentEra = this.currentEra + 1;
        this.eras.add(ClubEra.start(this.currentEra, EraStartReason.REVIVED, at));
        this.updatedAt = at;
    }

    public void updateProfile(String name, String tagline, String description, String category,
                              List<String> tags, UUID avatarMediaId, UUID bannerMediaId, Instant now) {
        if (status != ClubStatus.ACTIVE && status != ClubStatus.PENDING_APPROVAL) {
            throw new BusinessRuleException("CLUB_NOT_EDITABLE", "Only pending or active clubs can be edited.");
        }
        if (name != null) this.name = name.trim();
        if (tagline != null) this.tagline = tagline;
        if (description != null) this.description = description;
        if (category != null) this.category = category;
        if (tags != null) this.tags = new ArrayList<>(tags);
        if (avatarMediaId != null) this.avatarMediaId = avatarMediaId;
        if (bannerMediaId != null) this.bannerMediaId = bannerMediaId;
        this.updatedAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    public void changeJoinPolicy(JoinPolicy policy, Instant now) {
        requireStatus(ClubStatus.ACTIVE, "CLUB_NOT_ACTIVE");
        this.joinPolicy = Objects.requireNonNull(policy);
        this.updatedAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    /** BR-03: pending and rejected clubs are visible only to their founder and to admins. */
    public boolean isVisibleTo(UUID userId, boolean admin) {
        return admin || status == ClubStatus.ACTIVE || status == ClubStatus.ARCHIVED || founderId.equals(userId);
    }

    private ClubEra currentEraRecord() {
        return eras.stream().filter(e -> e.number() == currentEra).findFirst()
            .orElseThrow(() -> new IllegalStateException("Missing era " + currentEra));
    }

    private void review(UUID adminId, String note, Instant at) {
        this.reviewedBy = adminId;
        this.reviewNote = note;
        this.reviewedAt = at;
        this.updatedAt = at;
    }

    private void requireStatus(ClubStatus expected, String code) {
        if (status != expected) {
            throw new BusinessRuleException(code, "Club is " + status + ", expected " + expected + ".");
        }
    }

    public UUID id() { return id; }
    public String slug() { return slug; }
    public String name() { return name; }
    public String tagline() { return tagline; }
    public String description() { return description; }
    public String category() { return category; }
    public List<String> tags() { return Collections.unmodifiableList(tags); }
    public UUID avatarMediaId() { return avatarMediaId; }
    public UUID bannerMediaId() { return bannerMediaId; }
    public ClubStatus status() { return status; }
    public JoinPolicy joinPolicy() { return joinPolicy; }
    public UUID founderId() { return founderId; }
    public int currentEra() { return currentEra; }
    public String reviewNote() { return reviewNote; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public List<ClubEra> eras() { return Collections.unmodifiableList(eras); }
}