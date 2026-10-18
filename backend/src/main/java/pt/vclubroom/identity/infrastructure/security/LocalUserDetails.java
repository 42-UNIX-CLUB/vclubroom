package pt.vclubroom.identity.infrastructure.security;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** Only used during authentication (password check); replaced by LocalPrincipal afterwards. */
record LocalUserDetails(UUID userId, String login, String passwordHash, boolean admin, boolean active)
    implements UserDetails {

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return admin
            ? List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN"))
            : List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return login; }
    @Override public boolean isEnabled() { return active; }   // DISABLED accounts cannot log in (BR-24)
}
