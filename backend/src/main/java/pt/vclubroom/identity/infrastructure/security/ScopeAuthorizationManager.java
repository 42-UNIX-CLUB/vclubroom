package pt.vclubroom.identity.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import pt.vclubroom.shared.security.ApiScopes;

/** Lets a token request through when the token has the scope its path needs, which it remembers for the 403. */
final class ScopeAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    static final String REQUIRED_SCOPE = ScopeAuthorizationManager.class.getName() + ".requiredScope";

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication, RequestAuthorizationContext context) {
        HttpServletRequest request = context.getRequest();
        String path = request.getRequestURI().substring(request.getContextPath().length());
        Optional<String> required = ApiScopes.required(request.getMethod(), path);
        if (required.isEmpty()) {
            return new AuthorizationDecision(false);
        }
        request.setAttribute(REQUIRED_SCOPE, required.get());
        String authority = "SCOPE_" + required.get();
        return new AuthorizationDecision(
            authentication.get().getAuthorities().stream().anyMatch(a -> authority.equals(a.getAuthority())));
    }
}
