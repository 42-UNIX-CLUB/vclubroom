package pt.vclubroom.identity.infrastructure.security;

import java.io.Serial;
import java.util.UUID;
import org.springframework.security.core.AuthenticatedPrincipal;
import pt.vclubroom.shared.security.AuthenticatedUser;

/** Principal stored in the session after a local login. getName() = internal UUID. */
public record LocalPrincipal(UUID userId, String login, boolean admin)
    implements AuthenticatedPrincipal, AuthenticatedUser {

    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public String getName() {
        return userId.toString();
    }
}
