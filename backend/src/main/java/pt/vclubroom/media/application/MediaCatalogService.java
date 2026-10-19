package pt.vclubroom.media.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.media.api.MediaCatalog;
import pt.vclubroom.media.api.MediaPurpose;
import pt.vclubroom.media.api.MediaView;
import pt.vclubroom.media.domain.MediaObject;
import pt.vclubroom.media.persistence.MediaObjectRepository;
import pt.vclubroom.shared.error.BusinessRuleException;

/** Implementation of the public API. Presigning is local signing: resolving URLs makes no network call. */
@Service
class MediaCatalogService implements MediaCatalog {

    private final MediaObjectRepository media;
    private final ObjectStorage storage;
    private final MediaProperties properties;
    private final Clock clock;

    MediaCatalogService(MediaObjectRepository media, ObjectStorage storage, MediaProperties properties, Clock clock) {
        this.media = media;
        this.storage = storage;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional                                // joins the caller's transaction (e.g. PostService.createPost)
    public List<MediaView> attach(List<UUID> mediaIds, UUID ownerId, MediaPurpose purpose) {
        if (mediaIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, MediaObject> byId = media.findAllForUpdate(mediaIds).stream()
            .collect(Collectors.toMap(MediaObject::id, Function.identity()));
        if (byId.size() != Set.copyOf(mediaIds).size()) {
            throw new BusinessRuleException("MEDIA_NOT_ATTACHABLE", "Some media do not exist.");
        }
        Instant now = clock.instant();
        return mediaIds.stream().map(id -> {
            MediaObject object = byId.get(id);
            object.attach(ownerId, purpose, now);
            return view(object);
        }).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, MediaView> resolve(Collection<UUID> mediaIds) {
        if (mediaIds.isEmpty()) {
            return Map.of();
        }
        return media.findLive(mediaIds).stream().collect(Collectors.toMap(MediaObject::id, this::view));
    }

    private MediaView view(MediaObject m) {
        String url = m.purpose().isPublic()
            ? storage.publicUrl(m.bucket(), m.storageKey())
            : storage.presignDownload(m.bucket(), m.storageKey(), properties.downloadUrlTtl());
        return new MediaView(m.id(), m.kind(), m.contentType(), m.sizeBytes(), url);
    }
}
