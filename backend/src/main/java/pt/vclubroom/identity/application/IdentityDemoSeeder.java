package pt.vclubroom.identity.application;

import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.identity.api.PlatformRole;
import pt.vclubroom.identity.domain.User;
import pt.vclubroom.identity.persistence.UserRepository;
import pt.vclubroom.shared.demo.DemoData;

/** Demo people with a password login (APP_DEMO_PASSWORD, "demo-pass" by default); alice is an admin. */
@Component
@Profile(DemoData.PROFILE)
@Order(DemoData.USERS)
class IdentityDemoSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IdentityDemoSeeder.class);

    private record Person(String login, String name, PlatformRole role) { }

    private static final List<Person> PEOPLE = List.of(
        new Person("alice", "Alice Admin", PlatformRole.ADMIN),
        new Person("bob", "Bob Martins", PlatformRole.USER),
        new Person("carol", "Carol Silva", PlatformRole.USER),
        new Person("dave", "Dave Costa", PlatformRole.USER));

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final String password;

    IdentityDemoSeeder(UserRepository users, PasswordEncoder passwordEncoder, Clock clock,
                       @Value("${app.demo.password:demo-pass}") String password) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (Person p : PEOPLE) {
            if (!users.existsByLoginIgnoreCase(p.login())) {
                users.save(User.createLocal(p.login(), p.name(), p.login() + "@demo.vclubroom.local",
                    passwordEncoder.encode(password), p.role(), clock.instant()));
                log.info("Demo data: user {}", p.login());
            }
        }
    }
}
