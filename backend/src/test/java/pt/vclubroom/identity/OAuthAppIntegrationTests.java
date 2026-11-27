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
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import pt.vclubroom.TestcontainersConfiguration;
import pt.vclubroom.identity.api.PlatformRole;
import pt.vclubroom.identity.application.OAuthAppService;
import pt.vclubroom.identity.domain.PersonalAccessToken;
import pt.vclubroom.identity.domain.User;
import pt.vclubroom.identity.persistence.PersonalAccessTokenRepository;
import pt.vclubroom.identity.persistence.UserRepository;

/** Registering OAuth apps (E20-S04): public and confidential, three per person, managed by their owner only. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OAuthAppIntegrationTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    UserRepository users;

    @Autowired
    PersonalAccessTokenRepository tokens;

    @Autowired
    RegisteredClientRepository clients;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void anOwnerRegistersEditsReSecretsAndDeletesApps() throws Exception {
        User me = account();

        String pub = register(me, """
            {"name":" Club stats ","description":"Charts of my clubs","homepageUrl":"https://stats.example",
             "redirectUris":["https://stats.example/callback"," http://localhost:5173/callback "],"confidential":false}""")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.app.clientId").value(startsWith("vcr_app_")))
            .andExpect(jsonPath("$.app.name").value("Club stats"))
            .andExpect(jsonPath("$.app.confidential").value(false))
            .andExpect(jsonPath("$.app.redirectUris").value(contains("http://localhost:5173/callback", "https://stats.example/callback")))
            .andExpect(jsonPath("$.clientSecret").doesNotExist())
            .andReturn().getResponse().getContentAsString();
        String conf = register(me, "{\"name\":\"Bot\",\"redirectUris\":[\"https://bot.example/oauth\"],\"confidential\":true}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.app.confidential").value(true))
            .andExpect(jsonPath("$.clientSecret").value(startsWith("vcr_secret_")))
            .andReturn().getResponse().getContentAsString();
        String confId = JsonPath.read(conf, "$.app.id");
        String secret = JsonPath.read(conf, "$.clientSecret");
        String stored = clients.findById(confId).getClientSecret();
        assertThat(stored).startsWith("{bcrypt}");                                   // only a hash is kept
        assertThat(passwordEncoder.matches(secret, stored)).isTrue();

        mvc.perform(get("/api/me/apps").with(user(me.id(), false))).andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(patch("/api/me/apps/" + confId).with(user(me.id(), false)).with(xsrf(mvc)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Bot two\",\"redirectUris\":[\"https://bot.example/oauth2\"]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Bot two"))
            .andExpect(jsonPath("$.redirectUris").value(contains("https://bot.example/oauth2")))
            .andExpect(jsonPath("$.confidential").value(true));
        String renewed = JsonPath.read(mvc.perform(post("/api/me/apps/" + confId + "/secret").with(user(me.id(), false)).with(xsrf(mvc)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.clientSecret");
        assertThat(renewed).isNotEqualTo(secret);
        assertThat(passwordEncoder.matches(secret, clients.findById(confId).getClientSecret())).isFalse();
        mvc.perform(post("/api/me/apps/" + JsonPath.read(pub, "$.app.id") + "/secret").with(user(me.id(), false)).with(xsrf(mvc)))
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("APP_IS_PUBLIC"));

        mvc.perform(delete("/api/me/apps/" + JsonPath.read(pub, "$.app.id")).with(user(me.id(), false)).with(xsrf(mvc)))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/me/apps").with(user(me.id(), false))).andExpect(jsonPath("$", hasSize(1)));
        assertThat(clients.findById(JsonPath.read(pub, "$.app.id"))).isNull();
    }

    @Test
    void namesAddressesAndRedirectUrisAreChecked() throws Exception {
        User me = account();

        for (String uris : new String[] {"[\"http://evil.example/cb\"]", "[\"https://app.example/cb#frag\"]", "[\"javascript:alert(1)\"]", "[]"}) {
            register(me, "{\"name\":\"x\",\"redirectUris\":" + uris + "}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("APP_REDIRECT_URIS_INVALID"));
        }
        register(me, "{\"name\":\" \",\"redirectUris\":[\"https://app.example/cb\"]}")
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("APP_NAME_INVALID"));
        register(me, "{\"name\":\"x\",\"homepageUrl\":\"http://app.example\",\"redirectUris\":[\"https://app.example/cb\"]}")
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("APP_HOMEPAGE_INVALID"));
        register(me, "{\"name\":\"Phone app\",\"redirectUris\":[\"com.example.clubroom:/callback\"]}")
            .andExpect(status().isCreated());                                         // a mobile app's own scheme
    }

    @Test
    void threeAppsAtMostAndOnlyTheirOwnerChangesThem() throws Exception {
        User me = account();
        String id = null;
        for (int i = 1; i <= OAuthAppService.MAX_APPS; i++) {
            id = JsonPath.read(register(me, "{\"name\":\"App " + i + "\",\"redirectUris\":[\"https://a.example/cb\"]}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.app.id");
        }
        register(me, "{\"name\":\"One too many\",\"redirectUris\":[\"https://a.example/cb\"]}")
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("APP_LIMIT_REACHED"));

        User other = account();
        mvc.perform(patch("/api/me/apps/" + id).with(user(other.id(), false)).with(xsrf(mvc)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Mine now\",\"redirectUris\":[\"https://evil.example/cb\"]}"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("APP_NOT_FOUND"));
        mvc.perform(delete("/api/me/apps/" + id).with(user(other.id(), false)).with(xsrf(mvc))).andExpect(status().isNotFound());
    }

    @Test
    void tokensCantManageApps() throws Exception {
        User me = account();
        String token = PersonalAccessToken.PREFIX + UUID.randomUUID().toString().replace("-", "");
        tokens.save(PersonalAccessToken.issue(me.id(), "t", Set.of("profile:read"), null, token, Instant.now()));

        mvc.perform(get("/api/me/apps").with(bearer(token)))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("TOKEN_NOT_ALLOWED"));
    }

    private ResultActions register(User owner, String json) throws Exception {
        return mvc.perform(post("/api/me/apps").with(user(owner.id(), false)).with(xsrf(mvc))
            .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private User account() {
        String login = "app-" + UUID.randomUUID().toString().substring(0, 8);
        return users.save(User.createLocal(login, "App " + login, login + "@example.test", "{noop}secret", PlatformRole.USER, Instant.now()));
    }
}
