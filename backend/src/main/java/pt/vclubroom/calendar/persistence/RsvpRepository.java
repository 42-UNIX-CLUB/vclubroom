package pt.vclubroom.calendar.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pt.vclubroom.calendar.api.RsvpStatus;
import pt.vclubroom.calendar.domain.Rsvp;

public interface RsvpRepository extends JpaRepository<Rsvp, Rsvp.Key> {

    /** One row per (event, status) that has at least one answer. */
    @Query("""
           select r.eventId as eventId, r.status as status, count(r) as total from Rsvp r
           where r.eventId in :eventIds
           group by r.eventId, r.status
           """)
    List<StatusCount> countByEvents(@Param("eventIds") Collection<UUID> eventIds);

    @Query("select r from Rsvp r where r.userId = :userId and r.eventId in :eventIds")
    List<Rsvp> mineByEvents(@Param("userId") UUID userId, @Param("eventIds") Collection<UUID> eventIds);

    /** Oldest answer first, so the attendee list reads in sign-up order. */
    @Query("""
           select r.userId from Rsvp r
           where r.eventId = :eventId and r.status in :statuses
           order by r.updatedAt
           """)
    List<UUID> findUserIds(@Param("eventId") UUID eventId, @Param("statuses") Collection<RsvpStatus> statuses);

    interface StatusCount {
        UUID getEventId();
        RsvpStatus getStatus();
        long getTotal();
    }
}
