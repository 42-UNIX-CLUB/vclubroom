package pt.vclubroom.support.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pt.vclubroom.support.domain.Ticket;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    @Query(value = "select nextval('support.ticket_number_seq')", nativeQuery = true)
    long nextNumber();

    List<Ticket> findByReporterIdOrderByCreatedAtDesc(UUID reporterId);

    Optional<Ticket> findByNumberAndReporterId(long number, UUID reporterId);

    long countByReporterIdAndCreatedAtAfter(UUID reporterId, Instant since);

    /** Tickets the tracker doesn't have yet and that are due, oldest first. SKIP LOCKED: instances share the work. */
    @Query(value = """
           select * from support.tickets
           where tracker_issue is null and (tracker_next_attempt_at is null or tracker_next_attempt_at <= :now)
           order by created_at
           limit :batch
           for update skip locked
           """, nativeQuery = true)
    List<Ticket> claimUnsent(@Param("now") Instant now, @Param("batch") int batch);
}
