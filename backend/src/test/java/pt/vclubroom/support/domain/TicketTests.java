package pt.vclubroom.support.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TicketTests {

    private static final Instant NOW = Instant.parse("2026-10-30T10:00:00Z");

    private static Ticket ticket() {
        return Ticket.open(17, UUID.randomUUID(), TicketType.BUG, "Login button does nothing", "Nothing happens", null, null, NOW);
    }

    @Test
    void aNewTicketIsReceivedUntilTheTrackerHasIt() {
        Ticket t = ticket();
        assertThat(t.ref()).isEqualTo("T-17");
        assertThat(t.status()).isEqualTo(TicketStatus.RECEIVED);

        t.trackerFailed("Forgejo unreachable", NOW);
        t.sentToTracker(4, NOW.plusSeconds(90));

        assertThat(t.status()).isEqualTo(TicketStatus.OPEN);
        assertThat(t.trackerIssue()).isEqualTo(4);
        assertThat(t.updatedAt()).isEqualTo(NOW.plusSeconds(90));
    }

    @Test
    void theTrackerDecidesTheStatusOnceItHasTheTicket() {
        Ticket t = ticket();
        assertThat(t.changeStatus(TicketStatus.IN_PROGRESS, NOW)).isFalse();      // not in the tracker yet
        t.sentToTracker(4, NOW);

        assertThat(t.changeStatus(TicketStatus.WAITING_ON_REPORTER, NOW)).isTrue();
        assertThat(t.changeStatus(TicketStatus.WAITING_ON_REPORTER, NOW)).isFalse();
        t.reporterReplied(NOW);
        assertThat(t.status()).isEqualTo(TicketStatus.OPEN);
        assertThat(t.changeStatus(TicketStatus.DECLINED, NOW)).isTrue();
        assertThat(t.status().closed()).isTrue();
        assertThat(t.status().label()).isEqualTo("Won't do");
    }

    @Test
    void failuresWaitLongerEachTimeButNeverMoreThanAnHour() {
        Ticket t = ticket();
        long[] expected = {1, 2, 4, 8, 16, 32, 60, 60, 60};
        for (long minutes : expected) {
            t.trackerFailed("Forgejo answered 503 Service Unavailable", NOW);
            assertThat(t).extracting("trackerNextAttemptAt").isEqualTo(NOW.plus(Duration.ofMinutes(minutes)));
        }
        assertThat(t.trackerAttempts()).isEqualTo(expected.length);
        assertThat(t.status()).isEqualTo(TicketStatus.RECEIVED);
    }
}
