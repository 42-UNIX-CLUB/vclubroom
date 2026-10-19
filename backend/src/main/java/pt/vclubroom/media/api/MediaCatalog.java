package pt.vclubroom.media.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** How other modules use media: attach uploaded files to what they create, and turn IDs into URLs. */
public interface MediaCatalog {

    /**
     * BR-19: validates that every object exists, belongs to ownerId, is READY (or already ATTACHED to the
     * same purpose, which makes retries idempotent) and matches the purpose; then marks them ATTACHED.
     * @throws pt.vclubroom.shared.error.BusinessRuleException MEDIA_NOT_ATTACHABLE
     */
    List<MediaView> attach(List<UUID> mediaIds, UUID ownerId, MediaPurpose purpose);

    /** Resolves IDs to displayable views (unknown or deleted IDs are absent from the map). */
    Map<UUID, MediaView> resolve(Collection<UUID> mediaIds);
}
