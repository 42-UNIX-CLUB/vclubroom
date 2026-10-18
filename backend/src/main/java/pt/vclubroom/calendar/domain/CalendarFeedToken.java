package pt.vclubroom.calendar.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** At most one feed per user. Only the SHA-256 of the token is stored; the raw token is shown once (R-16). */
@Entity
@Table(name = "feed_tokens", schema = "calendar")
public class CalendarFeedToken {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", nullable = false, length = 64)                    // hex SHA-256
    private String tokenHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CalendarFeedToken() {
        // JPA
    }

    public static CalendarFeedToken issue(UUID userId, String tokenHash, Instant now) {
        CalendarFeedToken t = new CalendarFeedToken();
        t.userId = userId;
        t.rotate(tokenHash, now);
        return t;
    }

    /** Replacing the hash invalidates the previous URL at once. */
    public void rotate(String newTokenHash, Instant now) {
        this.tokenHash = newTokenHash;
        this.createdAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    public UUID userId() { return userId; }
    public String tokenHash() { return tokenHash; }
    public Instant createdAt() { return createdAt; }
}
