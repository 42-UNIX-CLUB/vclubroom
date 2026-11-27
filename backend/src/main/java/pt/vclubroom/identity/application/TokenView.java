package pt.vclubroom.identity.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A token as its owner sees it, without its value: that is shown once, when created (E20-S02). */
public record TokenView(UUID id, String name, List<String> scopes, String lastFour, Instant createdAt, Instant expiresAt,
                        Instant lastUsedAt, boolean expired) {
}
