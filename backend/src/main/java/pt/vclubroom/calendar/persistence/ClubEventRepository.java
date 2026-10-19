package pt.vclubroom.calendar.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pt.vclubroom.calendar.domain.ClubEvent;

/** E11-S02 adds the range queries of the calendars, E11-S06 the future events of an archived club. */
public interface ClubEventRepository extends JpaRepository<ClubEvent, UUID> {
}
