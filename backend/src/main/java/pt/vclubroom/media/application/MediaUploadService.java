package pt.vclubroom.media.application;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.media.api.MediaKind;
import pt.vclubroom.media.api.MediaPurpose;
import pt.vclubroom.media.domain.MediaObject;
import pt.vclubroom.media.persistence.MediaObjectRepository;
import pt.vclubroom.shared.error.BusinessRuleException;
import pt.vclubroom.shared.error.NotFoundException;
import pt.vclubroom.shared.security.Actor;

/** Upload slots (presigned PUT) and their completion. The bytes go from the browser to storage, never through here. */
@Service
public class MediaUploadService {

    public record UploadSlot(UUID mediaId, PresignedUpload upload) { }

    private final MediaObjectRepository media;
    private final ObjectStorage storage;
    private final MediaProperties properties;
    private final Clock clock;

    public MediaUploadService(MediaObjectRepository media, ObjectStorage storage, MediaProperties properties, Clock clock) {
        this.media = media;
        this.storage = storage;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public UploadSlot createSlot(Actor actor, MediaPurpose purpose, String fileName, String contentType, long sizeBytes) {
        MediaKind kind = kindOf(contentType);
        if (!purpose.allows(kind)) {
            throw new BusinessRuleException("MEDIA_KIND_NOT_ALLOWED", kind + " is not allowed for " + purpose);
        }
        MediaProperties.Limit limit = properties.limits().get(kind);
        if (sizeBytes <= 0 || sizeBytes > limit.maxBytes()) {                       // R-12
            throw new BusinessRuleException("MEDIA_TOO_LARGE", "Maximum size for " + kind + " is " + limit.maxBytes() + " bytes.");
        }
        String bucket = purpose.isPublic() ? properties.publicBucket() : properties.privateBucket();
        MediaObject object = MediaObject.pending(actor.userId(), purpose, kind, contentType, sizeBytes, fileName,
            bucket, clock.instant());
        media.save(object);
        PresignedUpload upload = storage.presignUpload(bucket, object.storageKey(), contentType, properties.uploadUrlTtl());
        return new UploadSlot(object.id(), upload);
    }

    /**
     * The size is not part of the signature, so it is checked here, against what storage actually holds.
     * noRollbackFor: when the file is rejected, the DELETED status must still be committed.
     */
    @Transactional(noRollbackFor = BusinessRuleException.class)
    public MediaObject complete(Actor actor, UUID mediaId) {
        MediaObject object = media.findById(mediaId)
            .filter(m -> m.ownerId().equals(actor.userId()))
            .orElseThrow(() -> new NotFoundException("MEDIA_NOT_FOUND", "Media not found"));
        StoredObject stored = storage.head(object.bucket(), object.storageKey())
            .orElseThrow(() -> new BusinessRuleException("MEDIA_NOT_UPLOADED", "The file was not uploaded."));
        MediaProperties.Limit limit = properties.limits().get(object.kind());
        boolean typeOk = stored.contentType() != null && stored.contentType().equals(object.contentType());
        if (stored.sizeBytes() > limit.maxBytes() || !typeOk) {
            storage.delete(object.bucket(), object.storageKey());
            object.markDeleted();
            throw new BusinessRuleException("MEDIA_REJECTED", "The uploaded file does not match the declared type or size.");
        }
        object.markReady(stored.sizeBytes(), stored.contentType());
        return object;
    }

    private MediaKind kindOf(String contentType) {
        return properties.limits().entrySet().stream()
            .filter(e -> e.getValue().contentTypes().contains(contentType))
            .map(Map.Entry::getKey)
            .findFirst()
            .orElseThrow(() -> new BusinessRuleException("MEDIA_TYPE_NOT_ALLOWED", "Unsupported file type " + contentType));
    }
}
