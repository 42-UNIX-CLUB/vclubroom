package pt.vclubroom.identity.application;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.identity.domain.PersonalAccessToken;
import pt.vclubroom.identity.domain.UserStatus;
import pt.vclubroom.identity.events.AccessTokenExpiring;
import pt.vclubroom.identity.persistence.PersonalAccessTokenRepository;
import pt.vclubroom.identity.persistence.UserRepository;
import pt.vclubroom.shared.error.BusinessRuleException;
import pt.vclubroom.shared.error.InvalidRequestException;
import pt.vclubroom.shared.error.NotFoundException;
import pt.vclubroom.shared.security.ApiScopes;

/** Personal access tokens (E20): who a token belongs to and what it may do; creating, listing and revoking them. */
@Service
public class AccessTokenService implements BearerTokens {

    /** Active (neither revoked nor expired) tokens per person, like 42's intra keeps its apps few (D-51). */
    public static final int MAX_ACTIVE = 5;
    private static final Duration REMIND_BEFORE = Duration.ofDays(7);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PersonalAccessTokenRepository tokens;
    private final UserRepository users;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    AccessTokenService(PersonalAccessTokenRepository tokens, UserRepository users, ApplicationEventPublisher events,
                       Clock clock) {
        this.tokens = tokens;
        this.users = users;
        this.events = events;
        this.clock = clock;
    }

    /** The token's owner and scopes, when it is valid now: neither revoked nor expired, and its owner active. */
    @Override
    @Transactional
    public Optional<AuthenticatedToken> authenticate(String rawToken) {
        if (rawToken == null || !rawToken.startsWith(PersonalAccessToken.PREFIX)) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        return tokens.findByTokenHash(PersonalAccessToken.hash(rawToken))
            .filter(t -> t.usableAt(now))
            .filter(t -> users.findById(t.userId()).map(u -> u.status() == UserStatus.ACTIVE).orElse(false))
            .map(t -> {
                t.markUsed(now);                                     // flushed at commit, at most once a minute
                return new AuthenticatedToken(t.id(), t.userId(), t.scopes());
            });
    }

    /** A new token for userId. expiresAt null: never expires. Its value is in the result, and nowhere else. */
    @Transactional
    public CreatedToken create(UUID userId, String name, Set<String> scopes, Instant expiresAt) {
        Instant now = clock.instant();
        Set<String> known = new LinkedHashSet<>(ApiScopes.all());
        if (scopes == null || scopes.isEmpty() || !known.containsAll(scopes)) {
            throw new InvalidRequestException("TOKEN_SCOPES_INVALID", "Choose one or more scopes from the list.");
        }
        if (expiresAt != null && !expiresAt.isAfter(now)) {
            throw new InvalidRequestException("TOKEN_EXPIRY_PAST", "The expiry date must be in the future.");
        }
        if (tokens.countActive(userId, now) >= MAX_ACTIVE) {
            throw new BusinessRuleException("TOKEN_LIMIT_REACHED",
                "You already have " + MAX_ACTIVE + " active tokens. Revoke one to create another.");
        }
        known.retainAll(scopes);                                     // in the catalogue's order
        String raw = PersonalAccessToken.PREFIX + randomValue();
        PersonalAccessToken token = tokens.save(PersonalAccessToken.issue(userId, name.strip(), known, expiresAt, raw, now));
        return new CreatedToken(raw, view(token, now));
    }

    @Transactional(readOnly = true)
    public List<TokenView> list(UUID userId) {
        Instant now = clock.instant();
        return tokens.findByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(userId).stream().map(t -> view(t, now)).toList();
    }

    @Transactional
    public void revoke(UUID userId, UUID tokenId) {
        PersonalAccessToken token = tokens.findById(tokenId)
            .filter(t -> t.userId().equals(userId) && t.revokedAt() == null)
            .orElseThrow(() -> new NotFoundException("TOKEN_NOT_FOUND", "No such token."));
        token.revoke(clock.instant());
    }

    /** By an admin, or when the account is disabled. */
    @Transactional
    public int revokeAll(UUID userId) {
        return tokens.revokeAll(userId, clock.instant());
    }

    /** Tells the owners of tokens expiring within a week, once per token (TokenExpiryReminderJob). */
    @Transactional
    public void remindExpiring() {
        Instant now = clock.instant();
        for (PersonalAccessToken token : tokens.findToRemind(now, now.plus(REMIND_BEFORE))) {
            token.markReminded(now);
            events.publishEvent(new AccessTokenExpiring(token.id(), token.userId(), token.name(), token.expiresAt()));
        }
    }

    private static TokenView view(PersonalAccessToken t, Instant now) {
        return new TokenView(t.id(), t.name(), List.copyOf(t.scopes()), t.lastFour(), t.createdAt(), t.expiresAt(),
            t.lastUsedAt(), t.expiredAt(now));
    }

    /** 32 random bytes, URL-safe: 43 characters after the prefix. */
    private static String randomValue() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
