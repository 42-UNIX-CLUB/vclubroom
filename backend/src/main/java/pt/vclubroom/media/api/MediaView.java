package pt.vclubroom.media.api;

import java.util.UUID;

/** url: public URL (public purposes) or presigned GET valid for app.media.download-url-ttl (private ones). */
public record MediaView(UUID id, MediaKind kind, String contentType, long sizeBytes, String url) {
}
