package pt.vclubroom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ClubroomApplicationTests {

    @Autowired
    MockMvc mvc;

    @Test
    void apiRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/me"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void healthAndApiDocsArePublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    void currentUserExposesLoginAndRoles() throws Exception {
        // Same shape the 42 login produces: the principal is named by the "login" attribute.
        OAuth2User user = new DefaultOAuth2User(
            List.of(new SimpleGrantedAuthority("ROLE_USER")), Map.of("login", "alice"), "login");

        mvc.perform(get("/api/me").with(oauth2Login().oauth2User(user)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.login").value("alice"))
            .andExpect(jsonPath("$.roles[0]").value("ROLE_USER"));
    }

    @Test
    void csrfEndpointIssuesXsrfCookie() throws Exception {
        Cookie cookie = mvc.perform(get("/api/auth/csrf"))
            .andExpect(status().isNoContent())
            .andReturn().getResponse().getCookie("XSRF-TOKEN");

        assertThat(cookie).isNotNull();
        assertThat(cookie.getValue()).isNotBlank();
        assertThat(cookie.isHttpOnly()).isFalse();   // the SPA must be able to read it
    }

    @Test
    void unsafeRequestsNeedTheCsrfHeaderTheSpaSends() throws Exception {
        String token = mvc.perform(get("/api/auth/csrf"))
            .andReturn().getResponse().getCookie("XSRF-TOKEN").getValue();

        // Without the header: rejected by the CSRF filter.
        mvc.perform(post("/api/auth/csrf").cookie(new Cookie("XSRF-TOKEN", token)))
            .andExpect(status().isForbidden());

        // With the raw cookie value echoed in X-XSRF-TOKEN (what the SPA's api client does): the CSRF check
        // passes and the request reaches MVC, which answers 405 because the endpoint only supports GET.
        mvc.perform(post("/api/auth/csrf").cookie(new Cookie("XSRF-TOKEN", token)).header("X-XSRF-TOKEN", token))
            .andExpect(status().isMethodNotAllowed());
    }
}
