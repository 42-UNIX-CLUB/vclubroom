package pt.vclubroom.identity.application;

import java.time.Clock;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.identity.domain.FortyTwoProfile;
import pt.vclubroom.identity.domain.User;
import pt.vclubroom.identity.events.UserProfileSynced;
import pt.vclubroom.identity.events.UserRegistered;
import pt.vclubroom.identity.persistence.UserRepository;
import pt.vclubroom.shared.error.ConflictException;

/** Creates the user at the first Login with 42 and refreshes the 42 snapshot at every later login. */
@Service
public class UserProvisioningService {

    private final UserRepository users;
    private final ApplicationEventPublisher events;
    private final IdentityProperties properties;
    private final Clock clock;

    public UserProvisioningService(UserRepository users, ApplicationEventPublisher events,
                                   IdentityProperties properties, Clock clock) {
        this.users = users;
        this.events = events;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public ProvisionedUser provisionFromFortyTwo(FortyTwoProfile profile) {
        Instant now = clock.instant();
        User user = users.findByFtId(profile.ftId()).orElse(null);

        if (user == null) {
            if (users.existsByLoginIgnoreCase(profile.login())) {           // BR-23
                throw new ConflictException("LOGIN_TAKEN",
                    "A local account already uses the login " + profile.login() + ". Contact an admin.");
            }
            boolean bootstrapAdmin = properties.bootstrapLoginSet().contains(profile.login().toLowerCase());
            user = User.registerFromFortyTwo(profile, now, bootstrapAdmin);
            events.publishEvent(new UserRegistered(user.id(), user.login(), user.provider().name(), now));
        } else {
            user.syncFromFortyTwo(profile, now);
        }
        user.recordLogin(now);
        users.save(user);

        events.publishEvent(new UserProfileSynced(user.id(), profile.login(), profile.poolYear(),
            profile.poolMonth(), profile.campusNames(), profile.cursusSlugs(), profile.alumni(), now));

        return new ProvisionedUser(user.id(), user.login(), user.isAdmin(), user.isActive());
    }
}
