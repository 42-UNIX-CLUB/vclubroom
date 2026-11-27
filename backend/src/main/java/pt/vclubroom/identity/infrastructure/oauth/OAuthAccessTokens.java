package pt.vclubroom.identity.infrastructure.oauth;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.stereotype.Component;
import pt.vclubroom.identity.application.AuthenticatedToken;
import pt.vclubroom.identity.application.BearerTokens;
import pt.vclubroom.identity.domain.PersonalAccessToken;
import pt.vclubroom.identity.domain.UserStatus;
import pt.vclubroom.identity.persistence.UserRepository;
import pt.vclubroom.shared.security.ApiScopes;

/**
 * Apps' access tokens (E20-S05): opaque, looked up in the authorization server's store, valid for an hour until revoked.
 * They act as the person who let the app in, within the scopes they consented to.
 */
@Component
class OAuthAccessTokens implements BearerTokens {

    private final OAuth2AuthorizationService authorizations;
    private final UserRepository users;

    OAuthAccessTokens(OAuth2AuthorizationService authorizations, UserRepository users) {
        this.authorizations = authorizations;
        this.users = users;
    }

    @Override
    public Optional<AuthenticatedToken> authenticate(String rawToken) {
        if (rawToken.startsWith(PersonalAccessToken.PREFIX)) {
            return Optional.empty();
        }
        OAuth2Authorization authorization = authorizations.findByToken(rawToken, OAuth2TokenType.ACCESS_TOKEN);
        if (authorization == null) {
            return Optional.empty();
        }
        OAuth2Authorization.Token<OAuth2AccessToken> token = authorization.getAccessToken();
        if (token == null || !token.isActive()) {
            return Optional.empty();
        }
        UUID userId = UUID.fromString(authorization.getPrincipalName());
        if (users.findById(userId).map(u -> u.status() != UserStatus.ACTIVE).orElse(true)) {
            return Optional.empty();
        }
        Set<String> scopes = new LinkedHashSet<>(token.getToken().getScopes());
        scopes.retainAll(ApiScopes.all());
        return Optional.of(new AuthenticatedToken(UUID.fromString(authorization.getId()), userId, scopes));
    }
}
