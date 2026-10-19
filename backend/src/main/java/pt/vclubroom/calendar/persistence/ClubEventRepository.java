package pt.vclubroom.calendar.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pt.vclubroom.calendar.domain.ClubEvent;

public interface ClubEventRepository extends JpaRepository<ClubEvent, UUID> {

    /** Events overlapping [from, to), every club (the campus calendar's "all"). */
    @Query("""
           select e from ClubEvent e
           where e.startsAt < :to and e.endsAt > :from
           order by e.startsAt
           """)
    List<ClubEvent> findInRange(@Param("from") Instant from, @Param("to") Instant to);

    /** Events overlapping [from, to) of some clubs ("my clubs", one club's calendar). */
    @Query("""
           select e from ClubEvent e
           where e.clubId in :clubIds and e.startsAt < :to and e.endsAt > :from
           order by e.startsAt
           """)
    List<ClubEvent> findInRangeForClubs(@Param("clubIds") Collection<UUID> clubIds,
                                        @Param("from") Instant from, @Param("to") Instant to);

    /** What an archival cancels (R-10): still scheduled and not started yet. */
    @Query("""
           select e from ClubEvent e
           where e.clubId = :clubId and e.status = pt.vclubroom.calendar.domain.EventStatus.SCHEDULED
             and e.startsAt > :now
           """)
    List<ClubEvent> findFutureScheduled(@Param("clubId") UUID clubId, @Param("now") Instant now);
}
