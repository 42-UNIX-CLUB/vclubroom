package pt.vclubroom.media.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** A browser uploads with an HTTP PUT to url, sending exactly these headers, before expiresAt. */
public record PresignedUpload(String url, Map<String, List<String>> headers, Instant expiresAt) {
}
