package pt.vclubroom.identity.infrastructure.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * The consent page posts its answer with fetch (E20-S05). The site's CSP has form-action 'self', and browsers check it
 * on the redirect that follows a form too, so a form couldn't send people back to the app. Asked for JSON, the
 * authorization server's redirect (with the code, or access_denied) comes as {"redirectUri": …}, and the page follows it.
 */
final class ConsentAnswerFilter extends OncePerRequestFilter {

    private final ObjectMapper json;

    ConsentAnswerFilter(ObjectMapper json) {
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        return !("POST".equals(request.getMethod()) && (request.getContextPath() + "/oauth2/authorize").equals(request.getRequestURI())
            && accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        chain.doFilter(request, new HttpServletResponseWrapper(response) {
            @Override
            public void sendRedirect(String location) throws IOException {
                setStatus(HttpServletResponse.SC_OK);
                setContentType(MediaType.APPLICATION_JSON_VALUE);
                json.writeValue(getOutputStream(), Map.of("redirectUri", location));
            }
        });
    }
}
