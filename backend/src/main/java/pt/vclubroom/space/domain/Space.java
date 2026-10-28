package pt.vclubroom.space.domain;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import pt.vclubroom.space.api.ChannelType;

/** A community space (D-04): a read rule, a write rule and channels. Never deleted, only archived. */
@Entity
@Table(name = "spaces", schema = "space")
public class Space {

    @Id
    private UUID id;

    @Column(name = "club_id", nullable = false)
    private UUID clubId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String slug;

    private String description;

    @Column(nullable = false)
    private int position;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "level", column = @Column(name = "read_level", nullable = false)),
        @AttributeOverride(name = "groupIds", column = @Column(name = "read_group_ids", nullable = false, columnDefinition = "uuid[]"))
    })
    private AccessRule readRule;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "level", column = @Column(name = "write_level", nullable = false)),
        @AttributeOverride(name = "groupIds", column = @Column(name = "write_group_ids", nullable = false, columnDefinition = "uuid[]"))
    })
    private AccessRule writeRule;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = false)
    @JoinColumn(name = "space_id", insertable = false, updatable = false)
    @OrderBy("position ASC")
    private List<Channel> channels = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Version
    private Long version;

    protected Space() {
        // JPA
    }

    public static Space create(UUID clubId, String name, String description, int position,
                               AccessRule read, AccessRule write, Instant now) {
        Space s = new Space();
        s.id = UUID.randomUUID();
        s.clubId = clubId;
        s.name = name;
        s.slug = slugify(name);
        s.description = description;
        s.position = position;
        s.createdAt = now.truncatedTo(ChronoUnit.MICROS);
        s.changeAccess(read, write);
        return s;
    }

    /** R-23: a PUBLIC write rule under a non-public read rule is normalised to the read rule. */
    public void changeAccess(AccessRule read, AccessRule write) {
        this.readRule = read;
        this.writeRule = (write.level() == AccessLevel.PUBLIC && read.level() != AccessLevel.PUBLIC) ? read : write;
    }

    public Channel addChannel(ChannelType type, String channelName, Instant now) {
        Channel channel = Channel.create(id, type, channelName, slugify(channelName), channels.size(), now);
        channels.add(channel);
        return channel;
    }

    /** The slug stays: links to the space keep working after a rename. */
    public void describe(String newName, String newDescription) {
        if (newName != null) this.name = newName;
        if (newDescription != null) this.description = newDescription.isBlank() ? null : newDescription;
    }

    public void moveTo(int newPosition) {
        this.position = newPosition;
    }

    /** "Board room" → "board-room": what a new space's or channel's slug would be. */
    public static String slugOf(String name) {
        return slugify(name);
    }

    public void removeGroupFromRules(UUID groupId) {
        this.readRule = readRule.withoutGroup(groupId);
        this.writeRule = writeRule.withoutGroup(groupId);
    }

    public void archive(Instant now) {
        this.archivedAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    private static String slugify(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    public boolean isArchived() { return archivedAt != null; }
    public UUID id() { return id; }
    public UUID clubId() { return clubId; }
    public String name() { return name; }
    public String slug() { return slug; }
    public String description() { return description; }
    public int position() { return position; }
    public AccessRule readRule() { return readRule; }
    public AccessRule writeRule() { return writeRule; }
    public List<Channel> channels() { return Collections.unmodifiableList(channels); }
}
