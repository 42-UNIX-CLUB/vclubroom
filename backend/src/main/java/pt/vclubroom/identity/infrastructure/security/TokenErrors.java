package pt.vclubroom.identity.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;

/** The errors of token and rate-limited requests: problem details like the rest of the API, with RFC 6750's WWW-Authenticate. */
final class TokenErrors {

    private final ObjectMapper json;

    TokenErrors(ObjectMapper json) {
        this.json = json;
    }

    void invalidToken(HttpServletResponse response) throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"");
        write(response, problem(HttpStatus.UNAUTHORIZED, "TOKEN_INVALID", "This token is unknown, revoked or expired."));
    }

    void unauthenticated(HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
        throws IOException {
        invalidToken(response);
    }

    void forbidden(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e) throws IOException {
        Object scope = request.getAttribute(ScopeAuthorizationManager.REQUIRED_SCOPE);
        if (scope == null) {
            write(response, problem(HttpStatus.FORBIDDEN, "TOKEN_NOT_ALLOWED", "This can't be done with a token."));
            return;
        }
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"insufficient_scope\", scope=\"" + scope + "\"");
        ProblemDetail problem = problem(HttpStatus.FORBIDDEN, "INSUFFICIENT_SCOPE", "This token lacks the scope " + scope + ".");
        problem.setProperty("scope", scope);
        write(response, problem);
    }

    void tooManyRequests(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        write(response, problem(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
            "Too many requests. Try again in " + retryAfterSeconds + " s."));
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", code);
        return problem;
    }

    private void write(HttpServletResponse response, ProblemDetail problem) throws IOException {
        response.setStatus(problem.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        json.writeValue(response.getOutputStream(), problem);
    }
}
