package pt.vclubroom.calendar.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pt.vclubroom.shared.error.BusinessRuleException;
import pt.vclubroom.shared.error.InvalidRequestException;

class ClubEventTests {

    private static final Instant NOW = Instant.parse("2026-10-19T10:00:00Z");
    private static final Instant START = Instant.parse("2026-10-23T18:00:00Z");

    private static ClubEvent tournament() {
        return ClubEvent.schedule(UUID.randomUUID(), UUID.randomUUID(), 1, UUID.randomUUID(), " Blitz night ", "",
            "Cluster 2", null, START, START.plus(Duration.ofHours(3)), null, NOW);
    }

    @Test
    void anEventEndsAfterItStarts() {
        assertThatThrownBy(() -> ClubEvent.schedule(UUID.randomUUID(), UUID.randomUUID(), 1, UUID.randomUUID(), "Oops",
                null, null, null, START, START, null, NOW))
            .isInstanceOfSatisfying(InvalidRequestException.class, e -> assertThat(e.code()).isEqualTo("INVALID_EVENT_TIMES"));
        assertThat(tournament().title()).isEqualTo("Blitz night");
        assertThat(tournament().description()).isNull();                         // blank means none
    }

    @Test
    void aCancelledEventStaysButCannotChange() {
        ClubEvent event = tournament();
        event.cancel("Room taken", NOW);

        assertThat(event.status()).isEqualTo(EventStatus.CANCELLED);
        assertThat(event.cancelReason()).isEqualTo("Room taken");
        assertThatThrownBy(() -> event.cancel("Again", NOW))
            .isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.code()).isEqualTo("EVENT_CANCELLED"));
        assertThatThrownBy(() -> event.reschedule("Later", null, null, null, START, START.plusSeconds(60), NOW))
            .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void reschedulingChecksTheTimesToo() {
        assertThatThrownBy(() -> tournament().reschedule("Blitz", null, null, null, START, START.minusSeconds(1), NOW))
            .isInstanceOf(InvalidRequestException.class);
    }
}
