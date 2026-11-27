package pt.vclubroom.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pt.vclubroom.TestRequests.bearer;
import static pt.vclubroom.TestRequests.user;
import static pt.vclubroom.TestRequests.xsrf;

import com.jayway.jsonpath.JsonPath;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.util.UriComponentsBuilder;
import pt.vclubroom.TestcontainersConfiguration;
import pt.vclubroom.identity.api.PlatformRole;
import pt.vclubroom.identity.application.AppDetails;
import pt.vclubroom.identity.application.OAuthAppService;
import pt.vclubroom.identity.application.RegisteredApp;
import pt.vclubroom.identity.domain.User;
import pt.vclubroom.identity.infrastructure.security.LocalPrincipal;
import pt.vclubroom.identity.persistence.UserRepository;

/**
 * Signing in to an app with Virtual Clubroom (E20-S05): authorization code with PKCE, consent on the SPA's page, opaque
 * access tokens within the consented scopes, refresh tokens replaced at each use, and access taken back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OAuthFlowIntegrationTests {

    private static final String CALLBACK = "https://app.example/callback";

    @Autowired
    MockMvc mvc;

    @Autowired
    UserRepository users;

    @Autowired
    OAuthAppService apps;

    @Test
    void aPublicAppGetsOnlyTheScopesConsentedToUntilAccessIsTakenBack() throws Exception {
        User owner = account("Omar Owner");
        User ada = account("Ada");
        RegisteredApp app = apps.register(owner.id(), new AppDetails("Club stats", "Charts of my clubs", "https://app.example", List.of(CALLBACK)), false);
        String clientId = app.app().clientId();
        String verifier = verifier();

        String consentPage = mvc.perform(authorize(clientId, "forum:read profile:read", verifier).with(user(ada.id(), false)))
            .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        assertThat(consentPage).contains("/oauth/consent?");
        assertThat(query(consentPage, "client_id")).isEqualTo(clientId);
        mvc.perform(get("/api/oauth/consent").param("client_id", clientId).param("scope", "forum:read profile:read unknown:scope")
                .with(user(ada.id(), false)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Club stats"))
            .andExpect(jsonPath("$.ownerName").value("Omar Owner"))
            .andExpect(jsonPath("$.scopes").value(contains("profile:read", "forum:read")))
            .andExpect(jsonPath("$.granted", hasSize(0)));

        String code = consent(clientId, query(consentPage, "state"), ada, "forum:read");               // not profile:read
        String tokens = mvc.perform(post("/oauth2/token").param("grant_type", "authorization_code").param("code", code)
                .param("redirect_uri", CALLBACK).param("client_id", clientId).param("code_verifier", verifier))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.scope").value("forum:read"))
            .andExpect(jsonPath("$.refresh_token").doesNotExist())                     // public apps sign in again
            .andReturn().getResponse().getContentAsString();
        String access = JsonPath.read(tokens, "$.access_token");

        mvc.perform(get("/api/reactions").with(bearer(access))).andExpect(status().isOk());
        mvc.perform(get("/api/me").with(bearer(access)))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.scope").value("profile:read"));
        mvc.perform(get("/api/me/authorized-apps").with(user(ada.id(), false)))
            .andExpect(jsonPath("$[0].name").value("Club stats"))
            .andExpect(jsonPath("$[0].scopes").value(contains("forum:read")));

        mvc.perform(delete("/api/me/authorized-apps/" + app.app().id()).with(user(ada.id(), false)).with(xsrf(mvc)))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/reactions").with(bearer(access))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me/authorized-apps").with(user(ada.id(), false))).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void aConfidentialAppRefreshesAndEachRefreshTokenWorksOnce() throws Exception {
        User ada = account("Ada");
        RegisteredApp app = apps.register(account("Owner").id(), new AppDetails("Bot", null, null, List.of(CALLBACK)), true);
        String clientId = app.app().clientId();
        String basic = "Basic " + Base64.getEncoder().encodeToString((clientId + ":" + app.clientSecret()).getBytes(StandardCharsets.UTF_8));
        String verifier = verifier();
        String consentPage = mvc.perform(authorize(clientId, "profile:read", verifier).with(user(ada.id(), false)))
            .andReturn().getResponse().getRedirectedUrl();
        String code = consent(clientId, query(consentPage, "state"), ada, "profile:read");

        String first = mvc.perform(post("/oauth2/token").header(HttpHeaders.AUTHORIZATION, basic).param("grant_type", "authorization_code")
                .param("code", code).param("redirect_uri", CALLBACK).param("code_verifier", verifier))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String refresh = JsonPath.read(first, "$.refresh_token");
        mvc.perform(get("/api/me").with(bearer(JsonPath.read(first, "$.access_token"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(ada.id().toString()));

        String second = mvc.perform(post("/oauth2/token").header(HttpHeaders.AUTHORIZATION, basic)
                .param("grant_type", "refresh_token").param("refresh_token", refresh))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(second, "$.refresh_token")).isNotEqualTo(refresh);
        mvc.perform(get("/api/me").with(bearer(JsonPath.read(second, "$.access_token")))).andExpect(status().isOk());
        mvc.perform(post("/oauth2/token").header(HttpHeaders.AUTHORIZATION, basic)
                .param("grant_type", "refresh_token").param("refresh_token", refresh))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("invalid_grant"));

        String access = JsonPath.read(second, "$.access_token");                        // the app signs out
        mvc.perform(post("/oauth2/revoke").header(HttpHeaders.AUTHORIZATION, basic).param("token", access)).andExpect(status().isOk());
        mvc.perform(get("/api/me").with(bearer(access))).andExpect(status().isUnauthorized());
    }

    @Test
    void aLocalAccountSignsInToAppsToo() throws Exception {
        User ada = account("Ada");
        RequestPostProcessor local = authentication(UsernamePasswordAuthenticationToken.authenticated(
            new LocalPrincipal(ada.id(), ada.login(), false), null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        RegisteredApp app = apps.register(account("Owner").id(), new AppDetails("Stats", null, null, List.of(CALLBACK)), false);
        String verifier = verifier();
        String consentPage = mvc.perform(authorize(app.app().clientId(), "profile:read", verifier).with(local))
            .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();

        String code = consent(app.app().clientId(), query(consentPage, "state"), local, "profile:read");
        String access = JsonPath.read(mvc.perform(post("/oauth2/token").param("grant_type", "authorization_code").param("code", code)
                .param("redirect_uri", CALLBACK).param("client_id", app.app().clientId()).param("code_verifier", verifier))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.access_token");

        mvc.perform(get("/api/me").with(bearer(access))).andExpect(jsonPath("$.id").value(ada.id().toString()));
    }

    @Test
    void deletingAnAppEndsEveryonesAccessThroughIt() throws Exception {
        User owner = account("Owner");
        User ada = account("Ada");
        RegisteredApp app = apps.register(owner.id(), new AppDetails("Stats", null, null, List.of(CALLBACK)), false);
        String verifier = verifier();
        String consentPage = mvc.perform(authorize(app.app().clientId(), "forum:read", verifier).with(user(ada.id(), false)))
            .andReturn().getResponse().getRedirectedUrl();
        String code = consent(app.app().clientId(), query(consentPage, "state"), ada, "forum:read");
        String access = JsonPath.read(mvc.perform(post("/oauth2/token").param("grant_type", "authorization_code").param("code", code)
                .param("redirect_uri", CALLBACK).param("client_id", app.app().clientId()).param("code_verifier", verifier))
            .andReturn().getResponse().getContentAsString(), "$.access_token");

        apps.delete(owner.id(), app.app().id());

        mvc.perform(get("/api/reactions").with(bearer(access))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me/authorized-apps").with(user(ada.id(), false))).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void aMobileAppComesBackThroughItsOwnScheme() throws Exception {
        String callback = "com.example.clubroom:/callback";
        User ada = account("Ada");
        RegisteredApp app = apps.register(account("Owner").id(), new AppDetails("Phone", null, null, List.of(callback)), false);
        String verifier = verifier();
        String consentPage = mvc.perform(get("/oauth2/authorize").queryParam("response_type", "code").queryParam("client_id", app.app().clientId())
                .queryParam("redirect_uri", callback).queryParam("scope", "forum:read").queryParam("state", "app-state")
                .queryParam("code_challenge", challenge(verifier)).queryParam("code_challenge_method", "S256").with(user(ada.id(), false)))
            .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();

        mvc.perform(post("/oauth2/authorize").param("client_id", app.app().clientId()).param("state", query(consentPage, "state"))
                .param("scope", "forum:read").with(user(ada.id(), false)).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.redirectUri").value(startsWith(callback + "?code=")));
    }

    @Test
    void cancellingTellsTheAppAccessWasDenied() throws Exception {
        User ada = account("Ada");
        RegisteredApp app = apps.register(account("Owner").id(), new AppDetails("Stats", null, null, List.of(CALLBACK)), false);
        String consentPage = mvc.perform(authorize(app.app().clientId(), "forum:read", verifier()).with(user(ada.id(), false)))
            .andReturn().getResponse().getRedirectedUrl();

        mvc.perform(post("/oauth2/authorize").param("client_id", app.app().clientId()).param("state", query(consentPage, "state"))
                .with(user(ada.id(), false)).accept(MediaType.APPLICATION_JSON))                // no scope: Cancel
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.redirectUri").value(startsWith(CALLBACK + "?error=access_denied")))
            .andExpect(jsonPath("$.redirectUri").value(containsString("state=app-state")));
    }

    @Test
    void theServerDescribesItself() throws Exception {
        mvc.perform(get("/.well-known/oauth-authorization-server"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authorization_endpoint").value(endsWith("/oauth2/authorize")))
            .andExpect(jsonPath("$.token_endpoint").value(endsWith("/oauth2/token")))
            .andExpect(jsonPath("$.code_challenge_methods_supported").value(contains("S256")));
    }

    @Test
    void someoneNotLoggedInLogsInFirstAndComesBack() throws Exception {
        RegisteredApp app = apps.register(account("Owner").id(), new AppDetails("Stats", null, null, List.of(CALLBACK)), false);

        String login = mvc.perform(authorize(app.app().clientId(), "forum:read", verifier()).accept(MediaType.TEXT_HTML))
            .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();

        assertThat(login).contains("/login?returnTo=%2Foauth2%2Fauthorize%3Fresponse_type%3Dcode");
    }

    @Test
    void disablingAnAccountEndsItsAppsAccess() throws Exception {
        User admin = account("Admin");
        users.save(admin);
        User ada = account("Ada");
        RegisteredApp app = apps.register(account("Owner").id(), new AppDetails("Stats", null, null, List.of(CALLBACK)), false);
        String verifier = verifier();
        String consentPage = mvc.perform(authorize(app.app().clientId(), "forum:read", verifier).with(user(ada.id(), false)))
            .andReturn().getResponse().getRedirectedUrl();
        String code = consent(app.app().clientId(), query(consentPage, "state"), ada, "forum:read");
        String access = JsonPath.read(mvc.perform(post("/oauth2/token").param("grant_type", "authorization_code").param("code", code)
                .param("redirect_uri", CALLBACK).param("client_id", app.app().clientId()).param("code_verifier", verifier))
            .andReturn().getResponse().getContentAsString(), "$.access_token");

        mvc.perform(patch("/api/admin/users/" + ada.id()).with(user(admin.id(), true)).with(xsrf(mvc))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DISABLED\"}"))
            .andExpect(status().isOk());

        mvc.perform(get("/api/reactions").with(bearer(access))).andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authorize(String clientId, String scope, String verifier)
        throws Exception {
        return get("/oauth2/authorize").queryParam("response_type", "code").queryParam("client_id", clientId)    // read from the query string
            .queryParam("redirect_uri", CALLBACK).queryParam("scope", scope).queryParam("state", "app-state")
            .queryParam("code_challenge", challenge(verifier)).queryParam("code_challenge_method", "S256");
    }

    /** Answers as the SPA's consent page does, asking for JSON; returns the code the app receives. */
    private String consent(String clientId, String state, User who, String... scopes) throws Exception {
        return consent(clientId, state, user(who.id(), false), scopes);
    }

    private String consent(String clientId, String state, RequestPostProcessor who, String... scopes) throws Exception {
        var request = post("/oauth2/authorize").param("client_id", clientId).param("state", state).with(who)
            .accept(MediaType.APPLICATION_JSON);
        for (String scope : scopes) {
            request.param("scope", scope);
        }
        String back = JsonPath.read(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
            "$.redirectUri");
        assertThat(back).startsWith(CALLBACK + "?code=").contains("state=app-state");
        return query(back, "code");
    }

    /** A query parameter of a redirect, decoded as a browser does (the state ends with an encoded "="). */
    private static String query(String url, String name) {
        String value = UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst(name);
        return value == null ? null : URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String verifier() {
        return UUID.randomUUID() + "-" + UUID.randomUUID();
    }

    private static String challenge(String verifier) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    private User account(String name) {
        String login = "oa-" + UUID.randomUUID().toString().substring(0, 8);
        return users.save(User.createLocal(login, name, login + "@example.test", "{noop}secret", PlatformRole.USER, Instant.now()));
    }
}
