package pt.vclubroom.group.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Someone in a group. In a club group, an active member of that club (BR-13). */
@Entity
@Table(name = "group_members", schema = "groups")
@IdClass(GroupMember.Key.class)
public class GroupMember {

    public record Key(UUID groupId, UUID userId) implements Serializable { }

    @Id
    @Column(name = "group_id")
    private UUID groupId;

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "added_by")
    private UUID addedBy;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    protected GroupMember() {
        // JPA
    }

    public static GroupMember of(UUID groupId, UUID userId, UUID addedBy, Instant now) {
        GroupMember m = new GroupMember();
        m.groupId = groupId;
        m.userId = userId;
        m.addedBy = addedBy;
        m.addedAt = now.truncatedTo(ChronoUnit.MICROS);
        return m;
    }

    public UUID groupId() { return groupId; }
    public UUID userId() { return userId; }
    public Instant addedAt() { return addedAt; }
}
