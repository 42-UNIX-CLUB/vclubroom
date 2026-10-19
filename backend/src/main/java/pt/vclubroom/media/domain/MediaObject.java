package pt.vclubroom.media.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;
import pt.vclubroom.media.api.MediaKind;
import pt.vclubroom.media.api.MediaPurpose;
import pt.vclubroom.shared.error.BusinessRuleException;

/** One uploaded file. The bytes live in object storage under storageKey; this row is the bookkeeping. */
@Entity
@Table(name = "media_objects", schema = "media")
public class MediaObject {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MediaPurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MediaKind kind;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "original_filename")
    private String originalFilename;

    @Column(nullable = false)
    private String bucket;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MediaStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "attached_at")
    private Instant attachedAt;

    @Version
    private Long version;

    protected MediaObject() {
        // JPA
    }

    /** Key = {purpose}/{yyyy}/{MM}/{id}: readable in the bucket, and unique because of the id. */
    public static MediaObject pending(UUID ownerId, MediaPurpose purpose, MediaKind kind, String contentType,
                                      long sizeBytes, String originalFilename, String bucket, Instant now) {
        MediaObject m = new MediaObject();
        m.id = UUID.randomUUID();
        m.ownerId = ownerId;
        m.purpose = purpose;
        m.kind = kind;
        m.contentType = contentType;
        m.sizeBytes = sizeBytes;
        m.originalFilename = originalFilename;
        m.bucket = bucket;
        m.createdAt = now.truncatedTo(ChronoUnit.MICROS);
        var date = m.createdAt.atOffset(ZoneOffset.UTC);
        m.storageKey = "%s/%d/%02d/%s".formatted(purpose.name().toLowerCase(Locale.ROOT),
            date.getYear(), date.getMonthValue(), m.id);
        m.status = MediaStatus.PENDING;
        return m;
    }

    public void markReady(long verifiedSize, String verifiedType) {
        if (status != MediaStatus.PENDING) {
            throw new BusinessRuleException("MEDIA_NOT_PENDING", "Upload already completed.");
        }
        this.sizeBytes = verifiedSize;
        this.contentType = verifiedType;
        this.status = MediaStatus.READY;
    }

    /** BR-19. Re-attaching an already ATTACHED object for the same owner and purpose is a no-op (idempotent retries). */
    public void attach(UUID requester, MediaPurpose requestedPurpose, Instant now) {
        boolean sameOwnerAndPurpose = ownerId.equals(requester) && purpose == requestedPurpose;
        if (!sameOwnerAndPurpose || !(status == MediaStatus.READY || status == MediaStatus.ATTACHED)) {
            throw new BusinessRuleException("MEDIA_NOT_ATTACHABLE", "Media " + id + " cannot be attached here.");
        }
        if (status == MediaStatus.READY) {
            this.status = MediaStatus.ATTACHED;
            this.attachedAt = now.truncatedTo(ChronoUnit.MICROS);
        }
    }

    public void markDeleted() {
        this.status = MediaStatus.DELETED;
    }

    public UUID id() { return id; }
    public UUID ownerId() { return ownerId; }
    public MediaPurpose purpose() { return purpose; }
    public MediaKind kind() { return kind; }
    public String contentType() { return contentType; }
    public long sizeBytes() { return sizeBytes; }
    public String bucket() { return bucket; }
    public String storageKey() { return storageKey; }
    public MediaStatus status() { return status; }
}
