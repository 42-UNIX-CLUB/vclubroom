package pt.vclubroom.media.application;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import pt.vclubroom.media.api.MediaCatalog;
import pt.vclubroom.media.api.MediaPurpose;
import pt.vclubroom.media.api.MediaView;
import pt.vclubroom.shared.error.BusinessRuleException;

/**
 * Temporary bean so the application starts before media uploads exist. It fails closed: nothing can be
 * attached and no ID resolves. Delete it when E08 adds the real MediaCatalog.
 */
@Service
class PlaceholderMediaCatalog implements MediaCatalog {

    @Override
    public List<MediaView> attach(List<UUID> mediaIds, UUID ownerId, MediaPurpose purpose) {
        if (mediaIds.isEmpty()) {
            return List.of();
        }
        throw new BusinessRuleException("MEDIA_NOT_ATTACHABLE", "Media uploads are not available yet.");
    }

    @Override
    public Map<UUID, MediaView> resolve(Collection<UUID> mediaIds) {
        return Map.of();
    }
}
