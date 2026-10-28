package pt.vclubroom.space.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import pt.vclubroom.space.api.ChannelType;

/** A forum or chat inside a space. It has no rules of its own: it inherits the space's (D-28). */
@Entity
@Table(name = "channels", schema = "space")
public class Channel {

    @Id
    private UUID id;

    @Column(name = "space_id", nullable = false)
    private UUID spaceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChannelType type;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String slug;

    private String description;

    @Column(nullable = false)
    private int position;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    protected Channel() {
        // JPA
    }

    static Channel create(UUID spaceId, ChannelType type, String name, String slug, int position, Instant now) {
        Channel c = new Channel();
        c.id = UUID.randomUUID();
        c.spaceId = spaceId;
        c.type = type;
        c.name = name;
        c.slug = slug;
        c.position = position;
        c.createdAt = now.truncatedTo(ChronoUnit.MICROS);
        return c;
    }

    public void archive(Instant now) {
        this.archivedAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    /** The slug stays: links to the channel keep working after a rename. */
    public void rename(String newName) {
        this.name = newName;
    }

    public void moveTo(int newPosition) {
        this.position = newPosition;
    }

    public boolean isArchived() { return archivedAt != null; }
    public UUID id() { return id; }
    public UUID spaceId() { return spaceId; }
    public ChannelType type() { return type; }
    public String name() { return name; }
    public String slug() { return slug; }
    public int position() { return position; }
}
