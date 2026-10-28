package pt.vclubroom.group.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import pt.vclubroom.group.api.GroupScope;
import pt.vclubroom.shared.error.BusinessRuleException;

/**
 * A named set of people, used only as the audience of space rules: groups grant no permissions. Members are not
 * part of the aggregate (campus groups can be large); GroupMemberRepository manages them.
 */
@Entity
@Table(name = "groups", schema = "groups")
public class Group {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GroupScope scope;

    @Column(name = "club_id")
    private UUID clubId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String slug;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GroupSource source;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    private Long version;

    protected Group() {
        // JPA
    }

    public static Group clubGroup(UUID clubId, String name, String slug, String description, UUID createdBy, Instant now) {
        Group g = base(GroupScope.CLUB, name, slug, description, GroupSource.MANUAL, createdBy, now);
        g.clubId = clubId;
        return g;
    }

    public static Group manualCampusGroup(String name, String slug, String description, UUID createdBy, Instant now) {
        return base(GroupScope.CAMPUS, name, slug, description, GroupSource.MANUAL, createdBy, now);
    }

    private static Group base(GroupScope scope, String name, String slug, String description, GroupSource source,
                              UUID createdBy, Instant now) {
        Group g = new Group();
        g.id = UUID.randomUUID();
        g.scope = scope;
        g.name = name;
        g.slug = slug;
        g.description = description;
        g.source = source;
        g.createdBy = createdBy;
        g.createdAt = now.truncatedTo(ChronoUnit.MICROS);
        return g;
    }

    /** The slug stays: space rules and links keep pointing at the same group. */
    public void describe(String newName, String newDescription) {
        ensureManuallyManaged();
        if (newName != null) this.name = newName;
        if (newDescription != null) this.description = newDescription;
    }

    public void ensureManuallyManaged() {
        if (source == GroupSource.SYNCED) {
            throw new BusinessRuleException("GROUP_SYNCED", "This group is synchronised from 42 and cannot be edited.");
        }
    }

    public boolean belongsToClub(UUID otherClubId) {
        return scope == GroupScope.CLUB && clubId.equals(otherClubId);
    }

    public UUID id() { return id; }
    public GroupScope scope() { return scope; }
    public UUID clubId() { return clubId; }
    public String name() { return name; }
    public String slug() { return slug; }
    public String description() { return description; }
    public GroupSource source() { return source; }
    public Instant createdAt() { return createdAt; }
}
