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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.calendar.domain.CalendarFeedToken;
import pt.vclubroom.calendar.persistence.FeedTokenRepository;

/** Personal iCal feed tokens (R-16): 32 random bytes, shown once, stored only as SHA-256. */
@Service
@Transactional(readOnly = true)
public class FeedTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final FeedTokenRepository tokens;
    private final Clock clock;
    private final String publicUrl;

    public FeedTokenService(FeedTokenRepository tokens, Clock clock, @Value("${app.public-url}") String publicUrl) {
        this.tokens = tokens;
        this.clock = clock;
        this.publicUrl = publicUrl;
    }

    /** Creates the feed or replaces its token, so the old URL stops working. Returns the feed URL, shown once. */
    @Transactional
    public String rotate(UUID userId) {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        String tokenHash = hash(token);
        Instant now = clock.instant();
        tokens.findById(userId).ifPresentOrElse(
            t -> t.rotate(tokenHash, now),
            () -> tokens.save(CalendarFeedToken.issue(userId, tokenHash, now)));
        return publicUrl + "/ical/feeds/" + token + ".ics";
    }

    @Transactional
    public void revoke(UUID userId) {
        tokens.deleteById(userId);
    }

    /** The owner of the presented token, if any. */
    public Optional<UUID> resolve(String token) {
        return tokens.findByTokenHash(hash(token)).map(CalendarFeedToken::userId);
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
