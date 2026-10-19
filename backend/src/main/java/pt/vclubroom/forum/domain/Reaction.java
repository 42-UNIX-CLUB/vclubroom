package pt.vclubroom.forum.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One user's emoji on a post or a comment. Rows are written with an idempotent native insert
 * (ReactionRepository.addIfAbsent); the entity is read-only here. Unique per (target, user, emoji): BR-18.
 */
@Entity
@Table(name = "reactions", schema = "forum")
public class Reaction {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false)
    private ReactionTarget targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String emoji;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Reaction() {
        // JPA
    }

    public UUID targetId() { return targetId; }
    public String emoji() { return emoji; }
}
