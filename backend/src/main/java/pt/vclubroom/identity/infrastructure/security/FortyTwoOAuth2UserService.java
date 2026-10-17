package pt.vclubroom.identity.infrastructure.security;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import pt.vclubroom.identity.application.ProvisionedUser;
import pt.vclubroom.identity.application.UserProvisioningService;
import pt.vclubroom.identity.domain.FortyTwoProfile;
import pt.vclubroom.shared.error.ConflictException;

/**
 * Plugged into oauth2Login().userInfoEndpoint().userService(...) in SecurityConfig.
 * Calls 42's /v2/me (through the default service), then provisions our own user.
 */
@Component
public class FortyTwoOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
    private final FortyTwoProfileMapper mapper;
    private final UserProvisioningService provisioning;

    public FortyTwoOAuth2UserService(FortyTwoProfileMapper mapper, UserProvisioningService provisioning) {
        this.mapper = mapper;
        this.provisioning = provisioning;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User raw = delegate.loadUser(request);
        FortyTwoProfile profile = mapper.map(raw.getAttributes());
        ProvisionedUser user;
        try {
            user = provisioning.provisionFromFortyTwo(profile);
        } catch (ConflictException e) {
            throw new OAuth2AuthenticationException(new OAuth2Error("login_conflict"), e.getMessage());
        }
        if (!user.active()) {
            throw new OAuth2AuthenticationException(new OAuth2Error("account_disabled"), "This account is disabled.");
        }
        return new ClubroomOAuth2User(user.id(), user.login(), user.admin());
    }
}
