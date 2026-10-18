package pt.vclubroom.membership.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import pt.vclubroom.membership.api.ClubPermission;
import pt.vclubroom.shared.error.BusinessRuleException;

/** A role in one club: the Owner and Member system roles, or a custom role built from permissions (D-18). */
@Entity
@Table(name = "club_roles", schema = "membership")
public class ClubRole {

    @Id
    private UUID id;

    @Column(name = "club_id", nullable = false)
    private UUID clubId;

    @Column(nullable = false)
    private String name;

    private String color;

    @Enumerated(EnumType.STRING)
    @Column(name = "system_role")
    private SystemRole systemRole;                 // null for custom roles

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "permissions", nullable = false, columnDefinition = "text[]")
    private List<String> permissions = new ArrayList<>();

    @Column(nullable = false)
    private int position;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    private Long version;

    protected ClubRole() {
        // JPA
    }

    public static ClubRole systemOwner(UUID clubId, Instant now) {
        return create(clubId, "Owner", "#f59e0b", SystemRole.OWNER, EnumSet.allOf(ClubPermission.class), 0, now);
    }

    public static ClubRole systemMember(UUID clubId, Instant now) {
        return create(clubId, "Member", "#64748b", SystemRole.MEMBER, EnumSet.noneOf(ClubPermission.class), 1000, now);
    }

    public static ClubRole custom(UUID clubId, String name, String color, Set<ClubPermission> permissions,
                                  int position, Instant now) {
        return create(clubId, name, color, null, permissions, position, now);
    }

    private static ClubRole create(UUID clubId, String name, String color, SystemRole systemRole,
                                   Set<ClubPermission> permissions, int position, Instant now) {
        ClubRole r = new ClubRole();
        r.id = UUID.randomUUID();
        r.clubId = clubId;
        r.name = name;
        r.color = color;
        r.systemRole = systemRole;
        r.permissions = new ArrayList<>(permissions.stream().map(Enum::name).sorted().toList());
        r.position = position;
        r.createdAt = now.truncatedTo(ChronoUnit.MICROS);
        return r;
    }

    /** BR-10: Owner always has every permission, whatever is stored. */
    public Set<ClubPermission> effectivePermissions() {
        if (isOwner()) {
            return EnumSet.allOf(ClubPermission.class);
        }
        EnumSet<ClubPermission> set = EnumSet.noneOf(ClubPermission.class);
        permissions.forEach(p -> set.add(ClubPermission.valueOf(p)));
        return set;
    }

    public void rename(String newName, String newColor) {
        if (systemRole != null) {
            throw new BusinessRuleException("SYSTEM_ROLE_IMMUTABLE", "System roles cannot be renamed.");
        }
        this.name = newName;
        this.color = newColor;
    }

    public void changePermissions(Set<ClubPermission> newPermissions) {
        if (isOwner()) {
            throw new BusinessRuleException("SYSTEM_ROLE_IMMUTABLE", "The Owner role always has every permission.");
        }
        this.permissions = new ArrayList<>(newPermissions.stream().map(Enum::name).sorted().toList());
    }

    public void ensureDeletable() {
        if (systemRole != null) {
            throw new BusinessRuleException("SYSTEM_ROLE_IMMUTABLE", "System roles cannot be deleted.");
        }
    }

    public boolean isOwner() { return systemRole == SystemRole.OWNER; }
    public boolean isMember() { return systemRole == SystemRole.MEMBER; }
    public boolean isSystem() { return systemRole != null; }
    public UUID id() { return id; }
    public UUID clubId() { return clubId; }
    public String name() { return name; }
    public String color() { return color; }
    public SystemRole systemRole() { return systemRole; }
    public int position() { return position; }
}
