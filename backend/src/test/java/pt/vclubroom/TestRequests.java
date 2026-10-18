package pt.vclubroom;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import pt.vclubroom.identity.infrastructure.security.ClubroomOAuth2User;

/** Request helpers shared by the HTTP-level integration tests of every module. */
public final class TestRequests {

    private TestRequests() {
    }

    /** A logged-in principal as Login with 42 creates it (identity's ClubroomOAuth2User). */
    public static RequestPostProcessor user(UUID id, boolean admin) {
        return oauth2Login().oauth2User(new ClubroomOAuth2User(id, "user-" + id.toString().substring(0, 8), admin));
    }

    /**
     * CSRF the way the SPA does it: GET /api/auth/csrf, then echo the cookie in X-XSRF-TOKEN. Never use
     * spring-security-test's csrf() here: it swaps the shared context's cookie repository for a session-based one,
     * and every test that runs after it in the same context stops receiving the XSRF-TOKEN cookie.
     */
    public static RequestPostProcessor xsrf(MockMvc mvc) throws Exception {
        String token = mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN").getValue();
        return request -> {
            request.setCookies(new Cookie("XSRF-TOKEN", token));
            request.addHeader("X-XSRF-TOKEN", token);
            return request;
        };
    }

    /** A short random suffix for names, because the integration tests share one database. */
    public static String suffix() {
        return UUID.randomUUID().toString().substring(0, 6);
    }
}
