package pt.vclubroom.identity.events;

import java.time.Instant;
import java.util.UUID;

/** A personal access token expires within a week (E20-S02): its owner is told once. */
public record AccessTokenExpiring(UUID tokenId, UUID userId, String tokenName, Instant expiresAt) {
}
