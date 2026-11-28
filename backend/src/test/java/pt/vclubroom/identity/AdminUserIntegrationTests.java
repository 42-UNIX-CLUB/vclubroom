package pt.vclubroom.identity;

import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pt.vclubroom.TestRequests.user;
import static pt.vclubroom.TestRequests.xsrf;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import pt.vclubroom.TestRequests;
import pt.vclubroom.TestcontainersConfiguration;
import pt.vclubroom.identity.application.UserProvisioningService;
import pt.vclubroom.identity.domain.FortyTwoProfile;

/**
 * The admin user console and local passwords over HTTP (E15-S02). Logins are real: their sessions live in Redis, so
 * revoking them (BR-24) is tested for what it is.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AdminUserIntegrationTests {

    private static final UUID ADMIN = UUID.randomUUID();

    @Autowired
    MockMvc mvc;

    @Autowired
    UserProvisioningService provisioning;

    private ResultActions create(String json) throws Exception {
        return mvc.perform(post("/api/admin/users").with(user(ADMIN, true)).with(xsrf(mvc))
            .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** A local account made by the admin; returns its ID. */
    private String account(String login, String password) throws Exception {
        String body = create("{\"username\":\"%s\",\"displayName\":\"Nina Partner\",\"email\":\"%s@example.test\",\"temporaryPassword\":\"%s\"}"
            .formatted(login, login, password)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private ResultActions change(String userId, String json) throws Exception {
        return mvc.perform(patch("/api/admin/users/{id}", userId).with(user(ADMIN, true)).with(xsrf(mvc))
            .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String xsrfToken() throws Exception {
        return mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN").getValue();
    }

    private ResultActions login(String login, String password) throws Exception {
        String xsrf = xsrfToken();
        return mvc.perform(post("/api/auth/login").cookie(new Cookie("XSRF-TOKEN", xsrf)).header("X-XSRF-TOKEN", xsrf)
            .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(login, password)));
    }

    private Cookie session(String login, String password) throws Exception {
        return login(login, password).andExpect(status().isOk()).andReturn().getResponse().getCookie("SESSION");
    }

    private ResultActions me(Cookie session) throws Exception {
        return mvc.perform(get("/api/me").cookie(session));
    }

    private ResultActions changeOwnPassword(Cookie session, String current, String next) throws Exception {
        String xsrf = xsrfToken();
        return mvc.perform(put("/api/me/password").cookie(session, new Cookie("XSRF-TOKEN", xsrf)).header("X-XSRF-TOKEN", xsrf)
            .contentType(MediaType.APPLICATION_JSON).content("{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}".formatted(current, next)));
    }

    @Test
    void adminsCreateLocalAccountsThatCanLogIn() throws Exception {
        String login = "nina-" + TestRequests.suffix();
        create("{\"username\":\"" + login.toUpperCase() + "\",\"displayName\":\"Nina\",\"temporaryPassword\":\"temporary-123\",\"admin\":true}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.login").value(login))                                // stored in lowercase
            .andExpect(jsonPath("$.provider").value("LOCAL"))
            .andExpect(jsonPath("$.role").value("ADMIN"))
            .andExpect(jsonPath("$.status").value("ACTIVE"));
        login(login, "temporary-123").andExpect(status().isOk()).andExpect(jsonPath("$.admin").value(true));

        create("{\"username\":\"" + login + "\",\"displayName\":\"Again\",\"temporaryPassword\":\"temporary-123\"}")
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LOGIN_TAKEN"));
        create("{\"username\":\"x\",\"displayName\":\"X\",\"temporaryPassword\":\"temporary-123\"}")
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_LOGIN"));
        create("{\"username\":\"short-" + TestRequests.suffix() + "\",\"displayName\":\"S\",\"temporaryPassword\":\"123456789\"}")
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));
        mvc.perform(post("/api/admin/users").with(user(UUID.randomUUID(), false)).with(xsrf(mvc))
                .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"sneaky\",\"displayName\":\"S\",\"temporaryPassword\":\"temporary-123\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void disablingOrChangingARoleEndsEverySessionAtOnce() throws Exception {
        String login = "omar-" + TestRequests.suffix();
        String id = account(login, "temporary-123");
        Cookie browser = session(login, "temporary-123");
        me(browser).andExpect(status().isOk());

        change(id, "{\"status\":\"DISABLED\"}").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DISABLED"));
        me(browser).andExpect(status().isUnauthorized());                               // BR-24, at once
        login(login, "temporary-123").andExpect(status().isUnauthorized());

        change(id, "{\"status\":\"ACTIVE\"}").andExpect(status().isOk());
        Cookie again = session(login, "temporary-123");
        change(id, "{\"platformRole\":\"ADMIN\"}").andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ADMIN"));
        me(again).andExpect(status().isUnauthorized());                                 // the session held the old role
        login(login, "temporary-123").andExpect(jsonPath("$.admin").value(true));

        change(ADMIN.toString(), "{\"platformRole\":\"USER\"}")
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("CANNOT_CHANGE_SELF"));
        change(UUID.randomUUID().toString(), "{\"status\":\"DISABLED\"}").andExpect(status().isNotFound());
    }

    @Test
    void localUsersChangeTheirPasswordAndAdminsResetIt() throws Exception {
        String login = "petra-" + TestRequests.suffix();
        String id = account(login, "temporary-123");
        Cookie laptop = session(login, "temporary-123");
        Cookie phone = session(login, "temporary-123");

        changeOwnPassword(laptop, "wrong-password", "my-own-secret").andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("WRONG_PASSWORD"));
        changeOwnPassword(laptop, "temporary-123", "short").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));
        changeOwnPassword(laptop, "temporary-123", "my-own-secret").andExpect(status().isNoContent());
        me(laptop).andExpect(status().isOk());                                          // this browser stays
        me(phone).andExpect(status().isUnauthorized());                                 // the others are logged out
        login(login, "temporary-123").andExpect(status().isUnauthorized());
        login(login, "my-own-secret").andExpect(status().isOk());

        mvc.perform(post("/api/admin/users/{id}/password", id).with(user(ADMIN, true)).with(xsrf(mvc))
                .contentType(MediaType.APPLICATION_JSON).content("{\"temporaryPassword\":\"fresh-start-1\"}"))
            .andExpect(status().isNoContent());
        me(laptop).andExpect(status().isUnauthorized());
        login(login, "fresh-start-1").andExpect(status().isOk());

        UUID student = provisioning.provisionFromFortyTwo(new FortyTwoProfile(System.nanoTime(), "stud-" + TestRequests.suffix(), null,
            "A Student", null, 2024, "march", false, false, List.of("Lisboa"), List.of())).id();
        mvc.perform(post("/api/admin/users/{id}/password", student).with(user(ADMIN, true)).with(xsrf(mvc))
                .contentType(MediaType.APPLICATION_JSON).content("{\"temporaryPassword\":\"fresh-start-1\"}"))
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("NOT_LOCAL_ACCOUNT"));
    }

    /** bcrypt reads at most 72 bytes: longer passwords are refused clearly, not with a 500 (accents count double). */
    @Test
    void passwordsLongerThanBcryptReadsAreRefused() throws Exception {
        String accented = "ção-á-é-í-ó-ú-".repeat(4);                            // 56 characters, 84 bytes
        create("{\"username\":\"long-%s\",\"displayName\":\"Long\",\"temporaryPassword\":\"%s\"}".formatted(TestRequests.suffix(), accented))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PASSWORD_TOO_LONG"));

        String login = "olga-" + TestRequests.suffix();
        String id = account(login, "a".repeat(72));                                     // exactly the limit: fine
        Cookie session = session(login, "a".repeat(72));
        changeOwnPassword(session, "a".repeat(72), "b".repeat(80)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("PASSWORD_TOO_LONG"));
        mvc.perform(post("/api/admin/users/{id}/password", id).with(user(ADMIN, true)).with(xsrf(mvc))
                .contentType(MediaType.APPLICATION_JSON).content("{\"temporaryPassword\":\"%s\"}".formatted("c".repeat(73))))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PASSWORD_TOO_LONG"));
    }

    @Test
    void theListFindsEveryAccountWhateverItsStatus() throws Exception {
        String prefix = "list" + TestRequests.suffix();
        account(prefix + "-ann", "temporary-123");
        String bob = account(prefix + "-bob", "temporary-123");
        change(bob, "{\"status\":\"DISABLED\"}").andExpect(status().isOk());

        mvc.perform(get("/api/admin/users").param("query", prefix).with(user(ADMIN, true)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.items[*].login", hasItems(prefix + "-ann", prefix + "-bob")))
            .andExpect(jsonPath("$.items[1].status").value("DISABLED"));
        mvc.perform(get("/api/admin/users").param("query", prefix).param("size", "1").with(user(ADMIN, true)))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/admin/users").with(user(UUID.randomUUID(), false))).andExpect(status().isForbidden());
    }
}
