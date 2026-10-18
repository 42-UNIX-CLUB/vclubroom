package pt.vclubroom.identity.events;

import java.time.Instant;
import java.util.UUID;

public record UserRegistered(UUID userId, String login, String provider, Instant occurredAt) {
}
