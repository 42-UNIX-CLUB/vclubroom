package pt.vclubroom.space.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Value object: who may read (or write). Column names are overridden per use (read_* / write_*) in Space. */
@Embeddable
public class AccessRule {

    @Enumerated(EnumType.STRING)
    @Column(name = "level", nullable = false)
    private AccessLevel level;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "group_ids", nullable = false, columnDefinition = "uuid[]")
    private List<UUID> groupIds = new ArrayList<>();

    protected AccessRule() {
        // JPA
    }

    private AccessRule(AccessLevel level, List<UUID> groupIds) {
        this.level = level;
        this.groupIds = level == AccessLevel.GROUPS ? new ArrayList<>(groupIds) : new ArrayList<>();
    }

    public static AccessRule publicRule() { return new AccessRule(AccessLevel.PUBLIC, List.of()); }
    public static AccessRule clubMembers() { return new AccessRule(AccessLevel.CLUB_MEMBERS, List.of()); }
    public static AccessRule groups(List<UUID> groupIds) { return new AccessRule(AccessLevel.GROUPS, groupIds); }

    public static AccessRule of(AccessLevel level, List<UUID> groupIds) {
        return new AccessRule(level, groupIds == null ? List.of() : groupIds);
    }

    public AccessRule withoutGroup(UUID groupId) {
        List<UUID> remaining = new ArrayList<>(groupIds);
        remaining.remove(groupId);
        return new AccessRule(level, remaining);
    }

    public AccessLevel level() { return level; }
    public List<UUID> groupIds() { return List.copyOf(groupIds); }
}
