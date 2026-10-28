package pt.vclubroom.calendar.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.calendar.domain.CalendarFeedToken;
import pt.vclubroom.calendar.persistence.FeedTokenRepository;
import pt.vclubroom.identity.api.UserContact;
import pt.vclubroom.identity.api.UserDirectory;

/**
 * The secret behind a personal calendar feed (R-16): 32 random bytes, given once in base64url, kept only as their
 * SHA-256. A new secret replaces the old one, so a leaked URL stops working; a disabled account's feed answers nothing.
 */
@Service
public class FeedTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final FeedTokenRepository tokens;
    private final UserDirectory users;
    private final Clock clock;

    FeedTokenService(FeedTokenRepository tokens, UserDirectory users, Clock clock) {
        this.tokens = tokens;
        this.users = users;
        this.clock = clock;
    }

    /** Creates or replaces my secret and returns it: the only time it is ever shown. */
    @Transactional
    public String rotate(UUID userId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        tokens.findById(userId).ifPresentOrElse(t -> t.replace(hash(token), now),
            () -> tokens.save(CalendarFeedToken.of(userId, hash(token), now)));
        return token;
    }

    @Transactional
    public void revoke(UUID userId) {
        tokens.deleteById(userId);
    }

    /** When my current feed was created, if I have one. */
    @Transactional(readOnly = true)
    public Optional<Instant> createdAt(UUID userId) {
        return tokens.findById(userId).map(CalendarFeedToken::createdAt);
    }

    /** The owner of a presented secret, if it is current and their account is active. */
    @Transactional(readOnly = true)
    public Optional<UUID> resolve(String token) {
        return tokens.findByTokenHash(hash(token))
            .map(CalendarFeedToken::userId)
            .filter(userId -> users.contactOf(userId).map(UserContact::active).orElse(false));
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
