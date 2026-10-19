package pt.vclubroom.membership.persistence;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pt.vclubroom.membership.domain.Membership;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {

    @Query("""
           select m from Membership m
           where m.clubId = :clubId and m.userId = :userId
             and m.status = pt.vclubroom.membership.domain.MembershipStatus.ACTIVE
           """)
    Optional<Membership> findActive(@Param("clubId") UUID clubId, @Param("userId") UUID userId);

    @Query("""
           select count(m) > 0 from Membership m
           where m.clubId = :clubId and m.userId = :userId
             and m.status = pt.vclubroom.membership.domain.MembershipStatus.ACTIVE
           """)
    boolean existsActive(@Param("clubId") UUID clubId, @Param("userId") UUID userId);

    @Query("""
           select m from Membership m join m.roleIds r
           where m.clubId = :clubId and r = :roleId
             and m.status = pt.vclubroom.membership.domain.MembershipStatus.ACTIVE
           """)
    List<Membership> findActiveWithRole(@Param("clubId") UUID clubId, @Param("roleId") UUID roleId);

    @Query("""
           select count(m) from Membership m join m.roleIds r
           where m.clubId = :clubId and r = :roleId
             and m.status = pt.vclubroom.membership.domain.MembershipStatus.ACTIVE
           """)
    long countActiveWithRole(@Param("clubId") UUID clubId, @Param("roleId") UUID roleId);

    @Query("""
           select count(m) from Membership m
           where m.clubId = :clubId and m.status = pt.vclubroom.membership.domain.MembershipStatus.ACTIVE
           """)
    long countActive(@Param("clubId") UUID clubId);

    @Query("""
           select m.userId from Membership m
           where m.clubId = :clubId and m.status = pt.vclubroom.membership.domain.MembershipStatus.ACTIVE
           """)
    Set<UUID> findActiveUserIds(@Param("clubId") UUID clubId);

    @Query("""
           select m.clubId from Membership m
           where m.userId = :userId and m.status = pt.vclubroom.membership.domain.MembershipStatus.ACTIVE
           """)
    Set<UUID> findActiveClubIds(@Param("userId") UUID userId);

    @Query("""
           select m from Membership m
           where m.userId = :userId and m.status = pt.vclubroom.membership.domain.MembershipStatus.ACTIVE
           """)
    List<Membership> findActiveOfUser(@Param("userId") UUID userId);

    @Query("""
           select m from Membership m
           where m.clubId = :clubId and m.status = pt.vclubroom.membership.domain.MembershipStatus.ACTIVE
           order by m.joinedAt
           """)
    Page<Membership> findActiveByClub(@Param("clubId") UUID clubId, Pageable pageable);
}
