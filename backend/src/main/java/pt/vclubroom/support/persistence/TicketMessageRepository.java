package pt.vclubroom.support.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pt.vclubroom.support.domain.TicketMessage;

public interface TicketMessageRepository extends JpaRepository<TicketMessage, UUID> {

    List<TicketMessage> findByTicketIdOrderByCreatedAt(UUID ticketId);

    boolean existsByTrackerCommentId(long trackerCommentId);

    Optional<TicketMessage> findFirstByTicketIdOrderByCreatedAtDesc(UUID ticketId);

    /** Reporters' messages due for the tracker, once their ticket's issue exists. SKIP LOCKED: instances share the work. */
    @Query(value = """
           select m.* from support.ticket_messages m join support.tickets t on t.id = m.ticket_id
           where m.author = 'REPORTER' and m.tracker_comment_id is null and t.tracker_issue is not null
             and (m.tracker_next_attempt_at is null or m.tracker_next_attempt_at <= :now)
           order by m.created_at
           limit :batch
           for update of m skip locked
           """, nativeQuery = true)
    List<TicketMessage> claimUnsent(@Param("now") Instant now, @Param("batch") int batch);
}
