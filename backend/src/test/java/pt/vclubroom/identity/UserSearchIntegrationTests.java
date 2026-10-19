package pt.vclubroom.identity;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pt.vclubroom.TestRequests.user;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import pt.vclubroom.TestRequests;
import pt.vclubroom.TestcontainersConfiguration;
import pt.vclubroom.identity.api.PlatformRole;
import pt.vclubroom.identity.domain.User;
import pt.vclubroom.identity.persistence.UserRepository;

/** GET /api/users (E02-S04-T2). Each test uses its own random prefix: the database is shared with other tests. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class UserSearchIntegrationTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    UserRepository users;

    @Autowired
    TransactionTemplate tx;

    private final UUID me = UUID.randomUUID();
    private String prefix;
    private User ada;

    private User saved(String login, String displayName) {
        return users.save(User.createLocal(login, displayName, login + "@example.test", "{noop}x", PlatformRole.USER, Instant.now()));
    }

    @BeforeEach
    void threePeople() {
        prefix = "u" + TestRequests.suffix();
        ada = saved(prefix + "ada", "Ada Lovelace");
        saved(prefix + "bob", "Bob Builder");
        User gone = saved(prefix + "eve", "Eve Gone");
        tx.executeWithoutResult(status -> users.findById(gone.id()).orElseThrow().disable(Instant.now()));
    }

    @Test
    void findsActiveUsersByLoginPrefixOrNameWithoutEmails() throws Exception {
        mvc.perform(get("/api/users").param("query", prefix).with(user(me, false)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].login", contains(prefix + "ada", prefix + "bob")))       // never the disabled one
            .andExpect(jsonPath("$[0].displayName").value("Ada Lovelace"))
            .andExpect(jsonPath("$[0].email").doesNotExist());
        mvc.perform(get("/api/users").param("query", "Lovelace").with(user(me, false)))
            .andExpect(jsonPath("$[*].login", contains(prefix + "ada")));
        mvc.perform(get("/api/users").param("query", prefix).param("limit", "1").with(user(me, false)))
            .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void oneLetterFindsNobody() throws Exception {
        mvc.perform(get("/api/users").param("query", "a").with(user(me, false)))
            .andExpect(jsonPath("$", empty()));
    }

    @Test
    void oneUserByIdAndVisitorsMustLogIn() throws Exception {
        mvc.perform(get("/api/users/{id}", ada.id()).with(user(me, false)))
            .andExpect(jsonPath("$.login").value(prefix + "ada"));
        mvc.perform(get("/api/users/{id}", UUID.randomUUID()).with(user(me, false)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        mvc.perform(get("/api/users").param("query", prefix))
            .andExpect(status().isUnauthorized());
    }
}
