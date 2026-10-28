package pt.vclubroom.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import pt.vclubroom.TestcontainersConfiguration;
import pt.vclubroom.identity.api.PlatformRole;
import pt.vclubroom.identity.application.ProvisionedUser;
import pt.vclubroom.identity.application.UserProvisioningService;
import pt.vclubroom.identity.domain.FortyTwoProfile;
import pt.vclubroom.identity.domain.User;
import pt.vclubroom.identity.infrastructure.security.ClubroomOAuth2User;
import pt.vclubroom.identity.persistence.UserRepository;
import pt.vclubroom.shared.error.ConflictException;

/**
 * Same annotations as ClubroomApplicationTests, so Spring reuses its cached context and containers.
 * Every test creates its own users with unique logins because the database is shared across tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class IdentityIntegrationTests {

    private static final String PASSWORD = "correct horse battery staple";

    @Autowired
    MockMvc mvc;

    @Autowired
    UserRepository users;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    UserProvisioningService provisioning;

    @Test
    void localLoginStartsASessionThatMeAndLogoutUse() throws Exception {
        User admin = localUser("admin", PlatformRole.ADMIN);

        Cookie session = login(admin.login(), PASSWORD)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(admin.id().toString()))
            .andExpect(jsonPath("$.provider").value("LOCAL"))
            .andExpect(jsonPath("$.admin").value(true))
            .andReturn().getResponse().getCookie("SESSION");
        assertThat(session).isNotNull();

        mvc.perform(get("/api/me").cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.login").value(admin.login()))
            .andExpect(jsonPath("$.displayName").value("Test admin"));

        String xsrf = xsrfToken();
        mvc.perform(post("/api/auth/logout").cookie(session, new Cookie("XSRF-TOKEN", xsrf)).header("X-XSRF-TOKEN", xsrf))
            .andExpect(status().isNoContent());

        mvc.perform(get("/api/me").cookie(session))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousApiCallsDoNotCreateASession() throws Exception {
        // The SPA calls /api/me on every page while logged out; each 401 must not leave a 14-day Redis session behind.
        mvc.perform(get("/api/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(cookie().doesNotExist("SESSION"));
    }

    @Test
    void wrongPasswordUnknownUserAndDisabledAccountAllGetTheSame401() throws Exception {
        User active = localUser("active", PlatformRole.USER);
        User disabled = localUser("disabled", PlatformRole.USER);
        disabled.disable(Instant.now());
        users.save(disabled);

        for (String[] attempt : new String[][] {
            {active.login(), "wrong password"}, {"nobody-" + suffix(), PASSWORD}, {disabled.login(), PASSWORD}}) {
            login(attempt[0], attempt[1])
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
    }

    @Test
    void adminUrlsNeedTheAdminRole() throws Exception {
        Cookie member = sessionOf(localUser("member", PlatformRole.USER));
        Cookie admin = sessionOf(localUser("boss", PlatformRole.ADMIN));

        mvc.perform(get("/api/admin/users").cookie(member)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users").cookie(admin)).andExpect(status().isOk());       // E15-S02's user list
    }

    @Test
    void loginWith42ProvisionsTheUserOnceAndMeReturnsIt() throws Exception {
        long ftId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
        String login = "ft-" + suffix();

        ProvisionedUser first = provisioning.provisionFromFortyTwo(profile(ftId, login, "First Name"));
        ProvisionedUser again = provisioning.provisionFromFortyTwo(profile(ftId, login, "Renamed At 42"));
        assertThat(again.id()).isEqualTo(first.id());   // same row, profile refreshed

        mvc.perform(get("/api/me").with(oauth2Login().oauth2User(new ClubroomOAuth2User(first.id(), login, false))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.provider").value("FORTY_TWO"))
            .andExpect(jsonPath("$.displayName").value("Renamed At 42"))
            .andExpect(jsonPath("$.admin").value(false));
    }

    @Test
    void a42LoginCannotTakeTheLoginOfALocalAccount() {
        User local = localUser("taken", PlatformRole.USER);
        FortyTwoProfile clash = profile(ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE),
            local.login().toUpperCase(), "Clash");   // BR-23 compares logins case-insensitively

        assertThatThrownBy(() -> provisioning.provisionFromFortyTwo(clash))
            .isInstanceOfSatisfying(ConflictException.class, e -> assertThat(e.code()).isEqualTo("LOGIN_TAKEN"));
    }

    @Test
    void loginWith42RedirectsToThe42AuthorizeEndpointWithOurCallback() throws Exception {
        mvc.perform(get("/oauth2/authorization/fortytwo"))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", startsWith("https://api.intra.42.fr/oauth/authorize?")))
            // app.public-url defaults to the Vite dev server, which proxies the callback to the backend
            .andExpect(header().string("Location",
                containsString("redirect_uri=http://localhost:5173/login/oauth2/code/fortytwo")));
    }

    private User localUser(String prefix, PlatformRole role) {
        String login = prefix + "-" + suffix();
        return users.save(User.createLocal(login, "Test " + prefix, login + "@example.test",
            passwordEncoder.encode(PASSWORD), role, Instant.now()));
    }

    private static FortyTwoProfile profile(long ftId, String login, String displayName) {
        return new FortyTwoProfile(ftId, login, login + "@student.42lisboa.com", displayName, null,
            2023, "september", false, false, List.of("Lisboa"), List.of("42cursus"));
    }

    /** What the SPA does: GET /api/auth/csrf, then send the cookie back with its value in X-XSRF-TOKEN. */
    private ResultActions login(String username, String password) throws Exception {
        String xsrf = xsrfToken();
        return mvc.perform(post("/api/auth/login")
            .cookie(new Cookie("XSRF-TOKEN", xsrf))
            .header("X-XSRF-TOKEN", xsrf)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)));
    }

    private Cookie sessionOf(User user) throws Exception {
        return login(user.login(), PASSWORD).andExpect(status().isOk()).andReturn().getResponse().getCookie("SESSION");
    }

    private String xsrfToken() throws Exception {
        return mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN").getValue();
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
