package pt.vclubroom.forum.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pt.vclubroom.forum.domain.Reaction;
import pt.vclubroom.forum.domain.ReactionTarget;

/** emoji holds the code ("party"), never the character: the set can change its pictures without a migration. */
public interface ReactionRepository extends JpaRepository<Reaction, UUID> {

    /** Projection: one row per (target, emoji) with its count. */
    interface ReactionCount {
        UUID getTargetId();
        String getEmoji();
        long getTotal();
    }

    @Query("""
           select r.targetId as targetId, r.emoji as emoji, count(r) as total
           from Reaction r
           where r.targetType = :targetType and r.targetId in :targetIds
           group by r.targetId, r.emoji
           """)
    List<ReactionCount> countByTargets(@Param("targetType") ReactionTarget targetType,
                                       @Param("targetIds") Collection<UUID> targetIds);

    @Query("""
           select r from Reaction r
           where r.targetType = :targetType and r.targetId in :targetIds and r.userId = :userId
           """)
    List<Reaction> mineByTargets(@Param("targetType") ReactionTarget targetType,
                                 @Param("targetIds") Collection<UUID> targetIds, @Param("userId") UUID userId);

    /** Idempotent: a second PUT finds the unique row and does nothing (BR-18). */
    default void addIfAbsent(ReactionTarget targetType, UUID targetId, UUID userId, String emoji) {
        insertIfAbsent(targetType.name(), targetId, userId, emoji);
    }

    @Modifying
    @Query(value = """
           insert into forum.reactions (id, target_type, target_id, user_id, emoji, created_at)
           values (gen_random_uuid(), :targetType, :targetId, :userId, :emoji, now())
           on conflict (target_type, target_id, user_id, emoji) do nothing
           """, nativeQuery = true)
    void insertIfAbsent(@Param("targetType") String targetType, @Param("targetId") UUID targetId,
                        @Param("userId") UUID userId, @Param("emoji") String emoji);

    @Modifying
    @Query("""
           delete from Reaction r
           where r.targetType = :targetType and r.targetId = :targetId and r.userId = :userId and r.emoji = :emoji
           """)
    void remove(@Param("targetType") ReactionTarget targetType, @Param("targetId") UUID targetId,
                @Param("userId") UUID userId, @Param("emoji") String emoji);
}
