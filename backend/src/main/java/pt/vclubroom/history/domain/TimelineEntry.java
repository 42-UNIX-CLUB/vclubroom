package pt.vclubroom.history.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One fact of a club's life. Written only by TimelineProjector's idempotent insert; read-only here. */
@Entity
@Immutable
@Table(name = "timeline_entries", schema = "history")
public class TimelineEntry {

    @Id
    private UUID id;

    @Column(name = "club_id", nullable = false)
    private UUID clubId;

    @Column(nullable = false)
    private int era;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TimelineEntryType type;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "subject_id")
    private UUID subjectId;

    /** e.g. {"reason": "Inactive for a year"} for ARCHIVED, {"reason": "INVITATION"} for OWNER_ADDED. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> data;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected TimelineEntry() {
        // JPA
    }

    public UUID id() { return id; }
    public int era() { return era; }
    public TimelineEntryType type() { return type; }
    public UUID actorId() { return actorId; }
    public UUID subjectId() { return subjectId; }
    public Map<String, Object> data() { return data; }
    public Instant occurredAt() { return occurredAt; }
}
