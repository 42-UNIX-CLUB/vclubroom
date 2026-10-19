package pt.vclubroom.media.web.dto;

import java.util.UUID;
import pt.vclubroom.media.api.MediaKind;
import pt.vclubroom.media.domain.MediaObject;
import pt.vclubroom.media.domain.MediaStatus;

public record MediaResponse(UUID id, MediaKind kind, String contentType, long sizeBytes, MediaStatus status) {

    public static MediaResponse from(MediaObject m) {
        return new MediaResponse(m.id(), m.kind(), m.contentType(), m.sizeBytes(), m.status());
    }
}
