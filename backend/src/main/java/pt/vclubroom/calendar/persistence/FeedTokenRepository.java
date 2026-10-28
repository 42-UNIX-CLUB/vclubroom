package pt.vclubroom.calendar.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pt.vclubroom.calendar.domain.CalendarFeedToken;

public interface FeedTokenRepository extends JpaRepository<CalendarFeedToken, UUID> {

    Optional<CalendarFeedToken> findByTokenHash(String tokenHash);
}
