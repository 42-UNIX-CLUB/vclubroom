package pt.vclubroom.membership.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pt.vclubroom.membership.domain.ClubRole;
import pt.vclubroom.membership.domain.SystemRole;

public interface ClubRoleRepository extends JpaRepository<ClubRole, UUID> {

    @Query("select r from ClubRole r where r.clubId = :clubId and r.systemRole = :systemRole")
    Optional<ClubRole> findSystemRole(@Param("clubId") UUID clubId, @Param("systemRole") SystemRole systemRole);

    /** Row lock that serialises ownership changes per club (BR-04). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ClubRole r where r.clubId = :clubId and r.systemRole = :systemRole")
    Optional<ClubRole> findSystemRoleForUpdate(@Param("clubId") UUID clubId, @Param("systemRole") SystemRole systemRole);

    List<ClubRole> findByClubIdOrderByPositionAsc(UUID clubId);

    /** Roles currently held by a user in a club (via an active membership). */
    @Query("""
           select r from Membership m join m.roleIds rid, ClubRole r
           where r.id = rid and m.clubId = :clubId and m.userId = :userId
             and m.status = pt.vclubroom.membership.domain.MembershipStatus.ACTIVE
           """)
    List<ClubRole> findHeldRoles(@Param("clubId") UUID clubId, @Param("userId") UUID userId);

    @Query(value = """
           select distinct m.user_id
           from membership.memberships m
           join membership.membership_roles mr on mr.membership_id = m.id
           join membership.club_roles r on r.id = mr.role_id
           where m.club_id = :clubId and m.status = 'ACTIVE'
             and (r.system_role = 'OWNER' or :permission = any(r.permissions))
           """, nativeQuery = true)
    Set<UUID> findHolderIds(@Param("clubId") UUID clubId, @Param("permission") String permission);
}
