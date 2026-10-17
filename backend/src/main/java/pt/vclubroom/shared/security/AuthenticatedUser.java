package pt.vclubroom.shared.security;

import java.io.Serializable;
import java.util.UUID;

/**
 * Implemented by every principal the identity module puts in the SecurityContext
 * (42 OAuth2 user and local user). Serializable because sessions are stored in Redis.
 * Controllers obtain it with @AuthenticationPrincipal AuthenticatedUser me.
 */
public interface AuthenticatedUser extends Serializable {

    UUID userId();

    boolean admin();

    default Actor actor() {
        return new Actor(userId(), admin());
    }
}
