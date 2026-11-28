package pt.vclubroom.identity.infrastructure.security;

import java.io.Serial;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.AuthenticatedPrincipal;
import pt.vclubroom.shared.security.AuthenticatedUser;

/** Who makes a request with a token (E20): its owner, within its scopes, and never as an admin. */
public record TokenPrincipal(UUID userId, UUID tokenId, Set<String> scopes)
    implements AuthenticatedPrincipal, AuthenticatedUser {

    @Serial
    private static final long serialVersionUID = 1L;

    public TokenPrincipal {
        scopes = Set.copyOf(scopes);
    }

    @Override
    public boolean admin() {
        return false;
    }

    @Override
    public String getName() {
        return userId.toString();
    }
}
