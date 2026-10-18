package pt.vclubroom.identity.infrastructure.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import pt.vclubroom.identity.persistence.UserRepository;

/** Loads LOCAL accounts only: a 42 user can never log in with a password. */
@Component
class LocalUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    LocalUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        return users.findLocalByLogin(username)
            .map(u -> new LocalUserDetails(u.id(), u.login(), u.passwordHash(), u.isAdmin(), u.isActive()))
            .orElseThrow(() -> new UsernameNotFoundException("Unknown local user"));
    }
}
