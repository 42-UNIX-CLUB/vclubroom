package pt.vclubroom.shared.web;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import pt.vclubroom.shared.error.InvalidRequestException;

/**
 * Opaque cursor over (created_at, id), ordered newest first.
 * Timestamps are truncated to microseconds when entities are created (PostgreSQL precision),
 * so cursor comparisons are exact.
 */
public record TimeCursor(Instant createdAt, UUID id) {

    public String encode() {
        String raw = createdAt.toString() + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static TimeCursor decode(String token) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", 2);
            return new TimeCursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (RuntimeException e) {
            throw new InvalidRequestException("INVALID_CURSOR", "The pagination cursor is invalid.");
        }
    }
}
