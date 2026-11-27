package pt.vclubroom.identity.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.identity.domain.PersonalAccessToken;
import pt.vclubroom.identity.domain.UserStatus;
import pt.vclubroom.identity.persistence.PersonalAccessTokenRepository;
import pt.vclubroom.identity.persistence.UserRepository;

/** Personal access tokens (E20): who a token belongs to and what it may do. */
@Service
public class AccessTokenService {

    private final PersonalAccessTokenRepository tokens;
    private final UserRepository users;
    private final Clock clock;

    AccessTokenService(PersonalAccessTokenRepository tokens, UserRepository users, Clock clock) {
        this.tokens = tokens;
        this.users = users;
        this.clock = clock;
    }

    /** The token's owner and scopes, when it is valid now: neither revoked nor expired, and its owner active. */
    @Transactional(readOnly = true)
    public Optional<AuthenticatedToken> authenticate(String rawToken) {
        if (rawToken == null || !rawToken.startsWith(PersonalAccessToken.PREFIX)) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        return tokens.findByTokenHash(PersonalAccessToken.hash(rawToken))
            .filter(t -> t.usableAt(now))
            .filter(t -> users.findById(t.userId()).map(u -> u.status() == UserStatus.ACTIVE).orElse(false))
            .map(t -> new AuthenticatedToken(t.id(), t.userId(), t.scopes()));
    }
}
