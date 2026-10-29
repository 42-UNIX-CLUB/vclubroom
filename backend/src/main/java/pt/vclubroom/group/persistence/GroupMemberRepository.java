package pt.vclubroom.group.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pt.vclubroom.group.domain.GroupMember;

public interface GroupMemberRepository extends JpaRepository<GroupMember, GroupMember.Key> {

    record MemberCount(UUID groupId, long count) { }

    /** For space rules: is this person in any of these groups? */
    @Query("select count(m) > 0 from GroupMember m where m.userId = :userId and m.groupId in :groupIds")
    boolean existsByUserInGroups(@Param("userId") UUID userId, @Param("groupIds") Collection<UUID> groupIds);

    @Query("select distinct m.userId from GroupMember m where m.groupId in :groupIds")
    Set<UUID> findUserIdsByGroups(@Param("groupIds") Collection<UUID> groupIds);

    List<GroupMember> findByGroupIdOrderByAddedAtAsc(UUID groupId);

    @Query("""
           select new pt.vclubroom.group.persistence.GroupMemberRepository$MemberCount(m.groupId, count(m))
           from GroupMember m where m.groupId in :groupIds group by m.groupId
           """)
    List<MemberCount> countByGroups(@Param("groupIds") Collection<UUID> groupIds);

    /** R-22: someone who leaves (or is removed from) a club leaves its groups; campus groups are untouched. */
    @Modifying
    @Query("""
           delete from GroupMember m where m.userId = :userId and m.groupId in
             (select g.id from Group g where g.scope = pt.vclubroom.group.api.GroupScope.CLUB and g.clubId = :clubId)
           """)
    int deleteFromClubGroups(@Param("clubId") UUID clubId, @Param("userId") UUID userId);

    /** The synced campus groups someone is in now (E13-S04); manual ones are never touched by the sync. */
    @Query("""
           select m.groupId from GroupMember m, Group g
           where g.id = m.groupId and m.userId = :userId and g.source = pt.vclubroom.group.domain.GroupSource.SYNCED
           """)
    Set<UUID> findSyncedGroupIdsOfUser(@Param("userId") UUID userId);

    @Modifying
    @Query("delete from GroupMember m where m.userId = :userId and m.groupId in :groupIds")
    void deleteMemberships(@Param("userId") UUID userId, @Param("groupIds") Collection<UUID> groupIds);
}
