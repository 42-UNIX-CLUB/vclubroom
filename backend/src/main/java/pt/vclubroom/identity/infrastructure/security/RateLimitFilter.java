package pt.vclubroom.identity.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.function.Function;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import pt.vclubroom.identity.infrastructure.security.RateLimiter.Bucket;
import pt.vclubroom.identity.infrastructure.security.RateLimiter.Decision;
import pt.vclubroom.shared.security.RateLimitProperties;

/**
 * Applies the rate limits (E20-S03) to the requests that have buckets, with the RateLimit-* headers of the IETF draft,
 * and a 429 with Retry-After once a bucket is empty. Not a bean, like BearerTokenFilter.
 */
final class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter limiter;
    private final TokenErrors errors;
    private final Function<HttpServletRequest, List<Bucket>> buckets;

    private RateLimitFilter(RateLimiter limiter, TokenErrors errors, Function<HttpServletRequest, List<Bucket>> buckets) {
        this.limiter = limiter;
        this.errors = errors;
        this.buckets = buckets;
    }

    /** Token requests: the token's bucket, its owner's, and the token's writes bucket for changes. */
    static RateLimitFilter forTokens(RateLimiter limiter, TokenErrors errors, RateLimitProperties limits) {
        return new RateLimitFilter(limiter, errors, request -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (!(auth instanceof TokenAuthentication token)) {
                return List.of();                                           // a CORS preflight
            }
            TokenPrincipal p = token.getPrincipal();
            boolean write = !HttpMethod.GET.matches(request.getMethod()) && !HttpMethod.HEAD.matches(request.getMethod());
            return write
                ? List.of(new Bucket("token:" + p.tokenId(), limits.token()), new Bucket("user:" + p.userId(), limits.user()),
                    new Bucket("token-writes:" + p.tokenId(), limits.tokenWrites()))
                : List.of(new Bucket("token:" + p.tokenId(), limits.token()), new Bucket("user:" + p.userId(), limits.user()));
        });
    }

    /**
     * /api/public without logging in, per IP (the proxy passes the visitor's, application.yml forward-headers-strategy).
     * Not from this machine: local development and the backend's own calls.
     */
    static RateLimitFilter forAnonymous(RateLimiter limiter, TokenErrors errors, RateLimitProperties limits) {
        return new RateLimitFilter(limiter, errors, request -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            boolean anonymous = auth == null || auth instanceof AnonymousAuthenticationToken;
            String path = request.getRequestURI().substring(request.getContextPath().length());   // without any forwarded prefix
            if (!anonymous || !path.startsWith("/api/public/") || isLoopback(request.getRemoteAddr())) {
                return List.of();
            }
            return List.of(new Bucket("ip:" + request.getRemoteAddr(), limits.anonymous()));
        });
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        List<Bucket> mine = buckets.apply(request);
        if (mine.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        Decision decision = limiter.take(mine);
        if (decision.limit() > 0) {                                         // 0: Redis was down, nothing checked
            response.setHeader("RateLimit-Limit", Long.toString(decision.limit()));
            response.setHeader("RateLimit-Remaining", Long.toString(decision.remaining()));
            response.setHeader("RateLimit-Reset", Long.toString(decision.resetSeconds()));
        }
        if (!decision.allowed()) {
            response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(decision.retryAfterSeconds()));
            errors.tooManyRequests(response, decision.retryAfterSeconds());
            return;
        }
        chain.doFilter(request, response);
    }

    /** The servlet container gives a literal address: no lookup needed. */
    private static boolean isLoopback(String address) {
        return address.startsWith("127.") || address.equals("::1") || address.equals("0:0:0:0:0:0:0:1");
    }
}
