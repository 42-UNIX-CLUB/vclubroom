package pt.vclubroom.identity.infrastructure.security;

import java.io.Serial;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;
import pt.vclubroom.shared.security.AuthenticatedUser;

/**
 * The principal stored in the session after Login with 42.
 * getName() returns our internal UUID, so Spring Session's principal index and STOMP user
 * destinations are keyed by user id. Only minimal attributes are kept (the session lives in Redis).
 */
public final class ClubroomOAuth2User implements OAuth2User, AuthenticatedUser {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID userId;
    private final String login;
    private final boolean admin;

    public ClubroomOAuth2User(UUID userId, String login, boolean admin) {
        this.userId = userId;
        this.login = login;
        this.admin = admin;
    }

    @Override
    public UUID userId() { return userId; }

    @Override
    public boolean admin() { return admin; }

    @Override
    public String getName() { return userId.toString(); }

    @Override
    public Map<String, Object> getAttributes() {
        return Map.of("userId", userId.toString(), "login", login);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return admin
            ? List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN"))
            : List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }
}
