package pt.vclubroom.identity.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import pt.vclubroom.identity.application.AuthenticatedToken;
import pt.vclubroom.identity.application.BearerTokens;

/**
 * Authenticates a request from its Authorization: Bearer header (E20-S01): a personal access token, or an app's access
 * token (E20-S05). An unknown, revoked or expired token gets
 * its 401 here. Not a bean: Spring Boot would also register it for every request, outside the token chain.
 */
final class BearerTokenFilter extends OncePerRequestFilter {

    private final List<BearerTokens> tokens;
    private final TokenErrors errors;

    BearerTokenFilter(List<BearerTokens> tokens, TokenErrors errors) {
        this.tokens = tokens;
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null) {                                        // a CORS preflight: nothing to authenticate
            chain.doFilter(request, response);
            return;
        }
        String raw = header.substring("Bearer ".length()).trim();
        Optional<AuthenticatedToken> token = tokens.stream().flatMap(t -> t.authenticate(raw).stream()).findFirst();
        if (token.isEmpty()) {
            errors.invalidToken(response);
            return;
        }
        AuthenticatedToken t = token.get();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new TokenAuthentication(new TokenPrincipal(t.userId(), t.tokenId(), t.scopes())));
        SecurityContextHolder.setContext(context);
        chain.doFilter(request, response);
    }
}
