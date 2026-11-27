package pt.vclubroom.identity;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pt.vclubroom.TestRequests.bearer;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import pt.vclubroom.TestcontainersConfiguration;
import pt.vclubroom.identity.api.PlatformRole;
import pt.vclubroom.identity.domain.PersonalAccessToken;
import pt.vclubroom.identity.domain.User;
import pt.vclubroom.identity.persistence.PersonalAccessTokenRepository;
import pt.vclubroom.identity.persistence.UserRepository;
import pt.vclubroom.shared.security.ApiScopes;

/** Calling the API with a token (E20-S01): as its owner, within its scopes, never as an admin. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TokenAccessIntegrationTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    UserRepository users;

    @Autowired
    PersonalAccessTokenRepository tokens;

    @Test
    void aTokenActsAsItsOwnerWithinItsScopes() throws Exception {
        User owner = user(PlatformRole.USER);
        String token = token(owner, null, "profile:read");

        mvc.perform(get("/api/me").with(bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(owner.id().toString()));
        mvc.perform(get("/api/notifications").with(bearer(token)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("INSUFFICIENT_SCOPE"))
            .andExpect(jsonPath("$.scope").value("notifications:read"))
            .andExpect(header().string("WWW-Authenticate", containsString("insufficient_scope")));
    }

    @Test
    void changesNeedTheWriteScopeButNoCsrf() throws Exception {
        User owner = user(PlatformRole.USER);
        String reader = token(owner, null, "notifications:read");
        String writer = token(owner, null, "notifications:read", "notifications:write");

        mvc.perform(post("/api/notifications/read-all").with(bearer(reader)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.scope").value("notifications:write"));
        mvc.perform(post("/api/notifications/read-all").with(bearer(writer)))
            .andExpect(status().isNoContent());
    }

    @Test
    void unknownRevokedOrExpiredTokensAndDisabledOwnersGetA401() throws Exception {
        User owner = user(PlatformRole.USER);
        String revoked = token(owner, null, "profile:read");
        PersonalAccessToken stored = tokens.findByTokenHash(PersonalAccessToken.hash(revoked)).orElseThrow();
        stored.revoke(Instant.now());
        tokens.save(stored);
        String expired = token(owner, Instant.now().minus(Duration.ofMinutes(1)), "profile:read");
        User disabled = user(PlatformRole.USER);
        String ofDisabled = token(disabled, null, "profile:read");
        disabled.disable(Instant.now());
        users.save(disabled);

        for (String t : new String[] {PersonalAccessToken.PREFIX + "unknown", "not-a-token", revoked, expired, ofDisabled}) {
            mvc.perform(get("/api/me").with(bearer(t)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"))
                .andExpect(header().string("WWW-Authenticate", containsString("invalid_token")));
        }
    }

    @Test
    void tokensNeverReachTheAdminConsoleNorPasswords() throws Exception {
        User admin = user(PlatformRole.ADMIN);
        String everything = token(admin, null, ApiScopes.all().toArray(String[]::new));

        mvc.perform(get("/api/admin/users").with(bearer(everything)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("TOKEN_NOT_ALLOWED"));
        mvc.perform(put("/api/me/password").with(bearer(everything))
                .contentType(MediaType.APPLICATION_JSON).content("{\"currentPassword\":\"a\",\"newPassword\":\"b\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("TOKEN_NOT_ALLOWED"));
        mvc.perform(get("/api/me").with(bearer(everything)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.admin").value(true));                     // who they are, not what the token may do
    }

    @Test
    void publicEndpointsTakeATokenToo() throws Exception {
        String token = token(user(PlatformRole.USER), null, "profile:read");

        mvc.perform(get("/api/public/categories").with(bearer(token))).andExpect(status().isOk());
    }

    @Test
    void browserAppsFromAnyOriginMayCallWithAToken() throws Exception {
        String token = token(user(PlatformRole.USER), null, "profile:read");

        mvc.perform(options("/api/me").header("Origin", "https://app.example")
                .header("Access-Control-Request-Method", "GET").header("Access-Control-Request-Headers", "authorization"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "https://app.example"));
        mvc.perform(get("/api/me").with(bearer(token)).header("Origin", "https://app.example"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "https://app.example"))
            .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    private User user(PlatformRole role) {
        String login = "tok-" + UUID.randomUUID().toString().substring(0, 8);
        return users.save(User.createLocal(login, "Token " + login, login + "@example.test", "{noop}secret", role, Instant.now()));
    }

    private String token(User owner, Instant expiresAt, String... scopes) {
        String raw = PersonalAccessToken.PREFIX + UUID.randomUUID().toString().replace("-", "");
        tokens.save(PersonalAccessToken.issue(owner.id(), "test", Set.of(scopes), expiresAt, raw, Instant.now()));
        return raw;
    }
}
