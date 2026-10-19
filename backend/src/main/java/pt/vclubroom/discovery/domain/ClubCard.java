package pt.vclubroom.discovery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Read-only view of one row of the read model (the public profile). Rows are written only by the native upserts
 * of ClubCardRepository; search, search_document and updated_at are not mapped because no reader needs them.
 */
@Entity
@Immutable
@Table(name = "club_cards", schema = "discovery")
public class ClubCard {

    @Id
    @Column(name = "club_id")
    private UUID clubId;

    private String slug;
    private String name;
    private String tagline;
    private String description;
    private String category;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private List<String> tags = new ArrayList<>();

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "banner_url")
    private String bannerUrl;

    private String status;

    @Column(name = "join_policy")
    private String joinPolicy;

    @Column(name = "current_era")
    private int currentEra;

    @Column(name = "member_count")
    private int memberCount;

    @Column(name = "founded_at")
    private Instant foundedAt;

    @Column(name = "last_activity_at")
    private Instant lastActivityAt;

    protected ClubCard() {
        // JPA
    }

    public UUID clubId() { return clubId; }
    public String slug() { return slug; }
    public String name() { return name; }
    public String tagline() { return tagline; }
    public String description() { return description; }
    public String category() { return category; }
    public List<String> tags() { return Collections.unmodifiableList(tags); }
    public String avatarUrl() { return avatarUrl; }
    public String bannerUrl() { return bannerUrl; }
    public String status() { return status; }
    public String joinPolicy() { return joinPolicy; }
    public int currentEra() { return currentEra; }
    public int memberCount() { return memberCount; }
    public Instant foundedAt() { return foundedAt; }
    public Instant lastActivityAt() { return lastActivityAt; }
}
