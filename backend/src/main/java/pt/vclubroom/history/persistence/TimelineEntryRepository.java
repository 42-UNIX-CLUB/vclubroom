package pt.vclubroom.history.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pt.vclubroom.history.domain.TimelineEntry;

public interface TimelineEntryRepository extends JpaRepository<TimelineEntry, UUID> {

    /** The dedup key comes from the event's identity: a redelivered event finds its row and changes nothing. */
    @Modifying
    @Query(value = """
           insert into history.timeline_entries (id, club_id, era, type, actor_id, subject_id, data, occurred_at, dedup_key)
           values (:id, :clubId, :era, :type, :actorId, :subjectId, cast(:data as jsonb), :occurredAt, :dedupKey)
           on conflict (club_id, dedup_key) do nothing
           """, nativeQuery = true)
    void insertIfAbsent(@Param("id") UUID id, @Param("clubId") UUID clubId, @Param("era") int era,
                        @Param("type") String type, @Param("actorId") UUID actorId, @Param("subjectId") UUID subjectId,
                        @Param("data") String data, @Param("occurredAt") Instant occurredAt,
                        @Param("dedupKey") String dedupKey);

    /** Keyset page, newest first (04-database §5.1), served by ix_timeline_club_occurred. */
    @Query("""
           select t from TimelineEntry t
           where t.clubId = :clubId
             and (t.occurredAt < :cursorAt or (t.occurredAt = :cursorAt and t.id < :cursorId))
           order by t.occurredAt desc, t.id desc
           """)
    List<TimelineEntry> findPage(@Param("clubId") UUID clubId, @Param("cursorAt") Instant cursorAt,
                                 @Param("cursorId") UUID cursorId, Limit limit);
}
