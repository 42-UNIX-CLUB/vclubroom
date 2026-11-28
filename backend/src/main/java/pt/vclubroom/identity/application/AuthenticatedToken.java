package pt.vclubroom.identity.application;

import java.util.Set;
import java.util.UUID;

/** A valid token: whose it is, and what it may do (E20). */
public record AuthenticatedToken(UUID tokenId, UUID userId, Set<String> scopes) {

    public AuthenticatedToken {
        scopes = Set.copyOf(scopes);
    }
}
