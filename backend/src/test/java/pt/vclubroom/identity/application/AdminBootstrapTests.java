package pt.vclubroom.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import pt.vclubroom.TestcontainersConfiguration;
import pt.vclubroom.identity.domain.User;
import pt.vclubroom.identity.persistence.UserRepository;

/** Same annotations as ClubroomApplicationTests, so Spring reuses its cached context and containers. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AdminBootstrapTests {

    @Autowired
    UserRepository users;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    Clock clock;

    @Test
    void createsTheLocalAdminOnceAndNeverResetsItsPassword() {
        String login = "bootstrap-" + UUID.randomUUID().toString().substring(0, 8);

        bootstrap(login, "first-password");
        User admin = users.findLocalByLogin(login).orElseThrow();
        assertThat(admin.isAdmin()).isTrue();
        assertThat(passwordEncoder.matches("first-password", admin.passwordHash())).isTrue();

        bootstrap(login, "changed-in-env-later");   // next start with another password in .env
        assertThat(passwordEncoder.matches("first-password",
            users.findLocalByLogin(login).orElseThrow().passwordHash())).isTrue();
    }

    @Test
    void doesNothingWithoutCredentials() {
        long before = users.count();
        bootstrap("", "");
        assertThat(users.count()).isEqualTo(before);
    }

    private void bootstrap(String username, String password) {
        new AdminBootstrap(users, passwordEncoder, new IdentityProperties(null, username, password), clock).run(null);
    }
}
