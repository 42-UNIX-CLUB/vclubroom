package pt.vclubroom.identity;

import java.util.HashSet;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

/**
 * Adds the application roles to the 42 user. Every login gets ROLE_USER; logins listed in
 * {@code app.admin.bootstrap-logins} also get ROLE_ADMIN.
 */
@Service
class FortyTwoUserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private static final String ROLE_USER = "ROLE_USER";
    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
    private final Set<String> adminLogins;

    FortyTwoUserService(AdminProperties properties) {
        this.adminLogins = Set.copyOf(properties.bootstrapLogins());
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) {
        OAuth2User user = delegate.loadUser(request);
        String nameAttribute = request.getClientRegistration()
            .getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();

        Set<GrantedAuthority> authorities = new HashSet<>(user.getAuthorities());
        authorities.add(new SimpleGrantedAuthority(ROLE_USER));
        if (adminLogins.contains(user.getName())) {
            authorities.add(new SimpleGrantedAuthority(ROLE_ADMIN));
        }
        return new DefaultOAuth2User(authorities, user.getAttributes(), nameAttribute);
    }
}
