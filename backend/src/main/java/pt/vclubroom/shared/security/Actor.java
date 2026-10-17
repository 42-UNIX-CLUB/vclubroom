package pt.vclubroom.shared.security;

import java.util.Objects;
import java.util.UUID;

/**
 * Who performs a use case. Application services receive an Actor, never a Spring Security type,
 * which keeps the application layer free of web/security concerns and trivially testable.
 */
public record Actor(UUID userId, boolean admin) {

    public Actor {
        Objects.requireNonNull(userId, "userId");
    }
}
