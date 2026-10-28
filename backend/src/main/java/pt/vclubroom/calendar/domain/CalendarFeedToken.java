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

/** A person's calendar feed (R-16): only the SHA-256 of its secret is kept. One per person; a new one replaces it. */
@Entity
@Table(name = "feed_tokens", schema = "calendar")
public class CalendarFeedToken {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @JdbcTypeCode(SqlTypes.CHAR)                                    // char(64): a hex SHA-256 always has 64 characters
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CalendarFeedToken() {
        // JPA
    }

    public static CalendarFeedToken of(UUID userId, String tokenHash, Instant now) {
        CalendarFeedToken t = new CalendarFeedToken();
        t.userId = userId;
        t.replace(tokenHash, now);
        return t;
    }

    public void replace(String newTokenHash, Instant now) {
        this.tokenHash = newTokenHash;
        this.createdAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    public UUID userId() { return userId; }
    public String tokenHash() { return tokenHash; }
    public Instant createdAt() { return createdAt; }
}
