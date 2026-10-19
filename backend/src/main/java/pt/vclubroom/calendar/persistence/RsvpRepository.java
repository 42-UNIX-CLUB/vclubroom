package pt.vclubroom.calendar.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pt.vclubroom.calendar.api.RsvpStatus;
import pt.vclubroom.calendar.domain.Rsvp;

public interface RsvpRepository extends JpaRepository<Rsvp, Rsvp.Key> {

    record StatusCount(UUID eventId, RsvpStatus status, long count) { }

    Optional<Rsvp> findByEventIdAndUserId(UUID eventId, UUID userId);

    List<Rsvp> findByEventIdAndStatusIn(UUID eventId, Collection<RsvpStatus> statuses);

    /** Counts per event and answer, for many events at once (calendars). */
    @Query("""
           select new pt.vclubroom.calendar.persistence.RsvpRepository$StatusCount(r.eventId, r.status, count(r))
           from Rsvp r where r.eventId in :eventIds group by r.eventId, r.status
           """)
    List<StatusCount> countByEvents(@Param("eventIds") Collection<UUID> eventIds);

    /** The viewer's own answers among these events. */
    @Query("select r from Rsvp r where r.userId = :userId and r.eventId in :eventIds")
    List<Rsvp> mineByEvents(@Param("userId") UUID userId, @Param("eventIds") Collection<UUID> eventIds);
}
