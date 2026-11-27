package pt.vclubroom.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import pt.vclubroom.TestcontainersConfiguration;
import pt.vclubroom.identity.api.PlatformRole;
import pt.vclubroom.identity.application.AccessTokenService;
import pt.vclubroom.identity.domain.PersonalAccessToken;
import pt.vclubroom.identity.domain.User;
import pt.vclubroom.identity.events.AccessTokenExpiring;
import pt.vclubroom.identity.persistence.PersonalAccessTokenRepository;
import pt.vclubroom.identity.persistence.UserRepository;

/** Personal access tokens (E20-S02): created, used, listed and revoked by their owner; revoked by admins too. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class PersonalAccessTokenIntegrationTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    UserRepository users;

    @Autowired
    PersonalAccessTokenRepository tokens;

    @Autowired
    AccessTokenService service;

    @Autowired
    ApplicationEvents events;

    @Test
    void aTokenIsShownOnceThenUsedListedAndRevoked() throws Exception {
        User me = account(PlatformRole.USER);

        String created = create(me, "{\"name\":\"  Backup script \",\"scopes\":[\"forum:read\",\"profile:read\"],\"expiresAt\":null}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.token").value(startsWith(PersonalAccessToken.PREFIX)))
            .andExpect(jsonPath("$.details.name").value("Backup script"))
            .andExpect(jsonPath("$.details.scopes").value(contains("profile:read", "forum:read")))   // the catalogue's order
            .andExpect(jsonPath("$.details.expired").value(false))
            .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(created, "$.token");
        String id = JsonPath.read(created, "$.details.id");
        assertThat(JsonPath.<String>read(created, "$.details.lastFour")).isEqualTo(token.substring(token.length() - 4));

        mvc.perform(get("/api/me").with(bearer(token))).andExpect(status().isOk());
        mvc.perform(get("/api/me/tokens").with(user(me.id(), false)))
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(id))
            .andExpect(jsonPath("$[0].lastUsedAt").isNotEmpty())
            .andExpect(jsonPath("$[0].token").doesNotExist());

        mvc.perform(delete("/api/me/tokens/" + id).with(user(me.id(), false)).with(xsrf(mvc)))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/me").with(bearer(token))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me/tokens").with(user(me.id(), false))).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void scopesNameAndExpiryAreChecked() throws Exception {
        User me = account(PlatformRole.USER);

        create(me, "{\"name\":\"x\",\"scopes\":[\"forum:delete\"]}")
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TOKEN_SCOPES_INVALID"));
        create(me, "{\"name\":\"x\",\"scopes\":[]}")
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        create(me, "{\"name\":\" \",\"scopes\":[\"forum:read\"]}")
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        create(me, "{\"name\":\"x\",\"scopes\":[\"forum:read\"],\"expiresAt\":\"2020-01-01T00:00:00Z\"}")
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TOKEN_EXPIRY_PAST"));
    }

    @Test
    void fiveActiveTokensAtMostAndExpiredOnesDontCount() throws Exception {
        User me = account(PlatformRole.USER);
        tokens.save(PersonalAccessToken.issue(me.id(), "old", Set.of("forum:read"), Instant.now().minus(Duration.ofDays(1)),
            PersonalAccessToken.PREFIX + UUID.randomUUID(), Instant.now().minus(Duration.ofDays(30))));
        String last = null;
        for (int i = 1; i <= AccessTokenService.MAX_ACTIVE; i++) {
            last = JsonPath.read(create(me, "{\"name\":\"t" + i + "\",\"scopes\":[\"forum:read\"]}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.details.id");
        }

        create(me, "{\"name\":\"one too many\",\"scopes\":[\"forum:read\"]}")
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("TOKEN_LIMIT_REACHED"));
        mvc.perform(get("/api/me/tokens").with(user(me.id(), false)))
            .andExpect(jsonPath("$", hasSize(6)))
            .andExpect(jsonPath("$[5].expired").value(true));                   // listed until revoked
        mvc.perform(delete("/api/me/tokens/" + last).with(user(me.id(), false)).with(xsrf(mvc))).andExpect(status().isNoContent());
        create(me, "{\"name\":\"room again\",\"scopes\":[\"forum:read\"]}").andExpect(status().isCreated());
    }

    @Test
    void onlyTheOwnerRevokesAndTokensCantManageTokens() throws Exception {
        User me = account(PlatformRole.USER);
        String created = create(me, "{\"name\":\"mine\",\"scopes\":[\"profile:read\"]}").andReturn().getResponse().getContentAsString();

        mvc.perform(delete("/api/me/tokens/" + JsonPath.read(created, "$.details.id")).with(user(account(PlatformRole.USER).id(), false))
                .with(xsrf(mvc)))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TOKEN_NOT_FOUND"));
        mvc.perform(get("/api/me/tokens").with(bearer(JsonPath.read(created, "$.token"))))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("TOKEN_NOT_ALLOWED"));
    }

    @Test
    void anAdminRevokesSomeonesTokensAndDisablingAnAccountDoesToo() throws Exception {
        User admin = account(PlatformRole.ADMIN);
        User ada = account(PlatformRole.USER);
        User bob = account(PlatformRole.USER);
        String adas = JsonPath.read(create(ada, "{\"name\":\"a\",\"scopes\":[\"profile:read\"]}").andReturn().getResponse().getContentAsString(), "$.token");
        String bobs = JsonPath.read(create(bob, "{\"name\":\"b\",\"scopes\":[\"profile:read\"]}").andReturn().getResponse().getContentAsString(), "$.token");

        mvc.perform(delete("/api/admin/users/" + ada.id() + "/tokens").with(user(admin.id(), true)).with(xsrf(mvc)))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/me").with(bearer(adas))).andExpect(status().isUnauthorized());

        mvc.perform(patch("/api/admin/users/" + bob.id()).with(user(admin.id(), true)).with(xsrf(mvc))
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DISABLED\"}"))
            .andExpect(status().isOk());
        assertThat(tokens.findByTokenHash(PersonalAccessToken.hash(bobs)).orElseThrow().revokedAt()).isNotNull();
    }

    @Test
    void ownersAreToldOnceAWeekBeforeATokenExpires() {
        User me = account(PlatformRole.USER);
        UUID soon = service.create(me.id(), "soon", Set.of("forum:read"), Instant.now().plus(Duration.ofDays(3))).details().id();
        UUID later = service.create(me.id(), "later", Set.of("forum:read"), Instant.now().plus(Duration.ofDays(30))).details().id();

        service.remindExpiring();
        service.remindExpiring();

        assertThat(events.stream(AccessTokenExpiring.class).map(AccessTokenExpiring::tokenId))
            .containsOnlyOnce(soon).doesNotContain(later);
    }

    private ResultActions create(User owner, String json) throws Exception {
        return mvc.perform(post("/api/me/tokens").with(user(owner.id(), false)).with(xsrf(mvc))
            .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private User account(PlatformRole role) {
        String login = "pat-" + UUID.randomUUID().toString().substring(0, 8);
        return users.save(User.createLocal(login, "Token " + login, login + "@example.test", "{noop}secret", role, Instant.now()));
    }
}
