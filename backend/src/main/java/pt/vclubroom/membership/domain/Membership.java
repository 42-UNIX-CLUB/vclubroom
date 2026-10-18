package pt.vclubroom.membership.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import pt.vclubroom.shared.error.BusinessRuleException;

/** One person in one club, holding roles. Owner = holder of the role whose systemRole is OWNER (no owner column). */
@Entity
@Table(name = "memberships", schema = "membership")
public class Membership {

    @Id
    private UUID id;

    @Column(name = "club_id", nullable = false)
    private UUID clubId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MembershipStatus status;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "membership_roles", schema = "membership", joinColumns = @JoinColumn(name = "membership_id"))
    @Column(name = "role_id", nullable = false)
    private Set<UUID> roleIds = new HashSet<>();

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "ended_by")
    private UUID endedBy;

    @Version
    private Long version;

    protected Membership() {
        // JPA
    }

    /** BR-09: every membership holds the Member system role. */
    public static Membership start(UUID clubId, UUID userId, UUID memberRoleId, Instant now) {
        Membership m = new Membership();
        m.id = UUID.randomUUID();
        m.clubId = clubId;
        m.userId = userId;
        m.status = MembershipStatus.ACTIVE;
        m.roleIds.add(memberRoleId);
        m.joinedAt = now.truncatedTo(ChronoUnit.MICROS);
        return m;
    }

    public void grantRole(UUID roleId) {
        requireActive();
        roleIds.add(roleId);
    }

    public void revokeRole(UUID roleId) {
        requireActive();
        roleIds.remove(roleId);
    }

    public void replaceCustomRoles(Set<UUID> systemRoleIdsToKeep, Set<UUID> newCustomRoleIds) {
        requireActive();
        Set<UUID> kept = new HashSet<>(roleIds);
        kept.retainAll(systemRoleIdsToKeep);
        kept.addAll(newCustomRoleIds);
        roleIds = kept;
    }

    public void leave(Instant now) {
        end(MembershipStatus.LEFT, userId, now);
    }

    public void remove(UUID by, Instant now) {
        end(MembershipStatus.REMOVED, by, now);
    }

    private void end(MembershipStatus endStatus, UUID by, Instant now) {
        requireActive();
        this.status = endStatus;
        this.endedBy = by;
        this.endedAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    private void requireActive() {
        if (status != MembershipStatus.ACTIVE) {
            throw new BusinessRuleException("MEMBERSHIP_NOT_ACTIVE", "This membership has ended.");
        }
    }

    public boolean holds(UUID roleId) { return roleIds.contains(roleId); }
    public UUID id() { return id; }
    public UUID clubId() { return clubId; }
    public UUID userId() { return userId; }
    public MembershipStatus status() { return status; }
    public Set<UUID> roleIds() { return Collections.unmodifiableSet(roleIds); }
    public Instant joinedAt() { return joinedAt; }
}
