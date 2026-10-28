package pt.vclubroom.calendar.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pt.vclubroom.calendar.domain.EventReminder;

public interface EventReminderRepository extends JpaRepository<EventReminder, UUID> {

    /** Reminders due now, oldest first. SKIP LOCKED: several backends never claim the same one (ix_reminders_due). */
    @Query(value = """
           select * from calendar.event_reminders
           where sent_at is null and remind_at <= :now
           order by remind_at
           limit :batch
           for update skip locked
           """, nativeQuery = true)
    List<EventReminder> claimDue(@Param("now") Instant now, @Param("batch") int batch);

    /** Before planning them again, or when the event is cancelled; sent ones stay as a record. */
    @Modifying
    @Query("delete from EventReminder r where r.eventId = :eventId and r.sentAt is null")
    void deleteUnsent(@Param("eventId") UUID eventId);

    List<EventReminder> findByEventIdOrderByRemindAtAsc(UUID eventId);
}
