package pt.vclubroom.identity.application;

import java.time.Clock;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.identity.api.PlatformRole;
import pt.vclubroom.identity.domain.User;
import pt.vclubroom.identity.persistence.UserRepository;

/** Creates the bootstrap local admin once, if APP_BOOTSTRAP_ADMIN_USERNAME/PASSWORD are set (R-09). */
@Component
class AdminBootstrap implements ApplicationRunner {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final IdentityProperties properties;
    private final Clock clock;

    AdminBootstrap(UserRepository users, PasswordEncoder passwordEncoder, IdentityProperties properties, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String username = properties.bootstrapLocalUsername();
        String password = properties.bootstrapLocalPassword();
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return;
        }
        if (users.existsByLoginIgnoreCase(username)) {
            return;   // created on an earlier start; changing the env var later does not reset the password
        }
        users.save(User.createLocal(username, "Administrator", null,
            passwordEncoder.encode(password), PlatformRole.ADMIN, clock.instant()));
    }
}
