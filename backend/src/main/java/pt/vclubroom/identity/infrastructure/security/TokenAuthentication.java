package pt.vclubroom.identity.infrastructure.security;

import java.io.Serial;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** An authenticated token request: one SCOPE_… authority per scope, as Spring Security's resource server does. */
final class TokenAuthentication extends AbstractAuthenticationToken {

    @Serial
    private static final long serialVersionUID = 1L;

    private final TokenPrincipal principal;

    TokenAuthentication(TokenPrincipal principal) {
        super(principal.scopes().stream().map(s -> new SimpleGrantedAuthority("SCOPE_" + s)).toList());
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public TokenPrincipal getPrincipal() {
        return principal;
    }
}
