package pt.vclubroom.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * A token someone created to call the API as themselves, within its scopes (E20). The token itself is shown once;
 * only its SHA-256 is kept, which is enough for a long random value.
 */
@Entity
@Table(name = "personal_access_tokens", schema = "identity")
public class PersonalAccessToken implements Persistable<UUID> {

    /** What every token starts with, so people and secret scanners recognise one. */
    public static final String PREFIX = "vcr_pat_";

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String name;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "last_four", nullable = false)
    private String lastFour;

    @Column(nullable = false)
    private String scopes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "reminded_at")
    private Instant remindedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Transient
    private boolean isNew = true;

    protected PersonalAccessToken() {
    }

    /** A new token for userId; rawToken is shown to them once and never stored. expiresAt null: never expires. */
    public static PersonalAccessToken issue(UUID userId, String name, Set<String> scopes, Instant expiresAt,
                                            String rawToken, Instant now) {
        PersonalAccessToken t = new PersonalAccessToken();
        t.id = UUID.randomUUID();
        t.userId = userId;
        t.name = name;
        t.tokenHash = hash(rawToken);
        t.lastFour = rawToken.substring(rawToken.length() - 4);
        t.scopes = String.join(" ", scopes);
        t.createdAt = now;
        t.expiresAt = expiresAt;
        return t;
    }

    /** The SHA-256 of a token, in hex: what is stored and looked up. */
    public static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);   // every JVM has SHA-256
        }
    }

    /** Neither revoked nor expired. */
    public boolean usableAt(Instant now) {
        return revokedAt == null && (expiresAt == null || expiresAt.isAfter(now));
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    @Override
    public UUID getId() { return id; }

    @Override
    public boolean isNew() { return isNew; }

    @PostLoad
    @PostPersist
    void markNotNew() { isNew = false; }

    public UUID id() { return id; }
    public UUID userId() { return userId; }
    public String name() { return name; }
    public String lastFour() { return lastFour; }
    public Set<String> scopes() { return new LinkedHashSet<>(Arrays.asList(scopes.split(" "))); }
    public Instant createdAt() { return createdAt; }
    public Instant expiresAt() { return expiresAt; }
    public Instant lastUsedAt() { return lastUsedAt; }
    public Instant remindedAt() { return remindedAt; }
    public Instant revokedAt() { return revokedAt; }
}
