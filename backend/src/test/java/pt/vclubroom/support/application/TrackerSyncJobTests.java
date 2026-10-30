package pt.vclubroom.support.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pt.vclubroom.TestcontainersConfiguration;
import pt.vclubroom.identity.api.UserDirectory;
import pt.vclubroom.identity.api.UserSummary;
import pt.vclubroom.shared.security.Actor;
import pt.vclubroom.support.domain.TicketType;

/** The tracker outbox on its own (E17-S02): sent once, retried with backoff, never given up. The tracker is mocked. */
@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {"app.support.sync-interval=PT1H", "app.public-url=https://clubroom.test/"})  // the test runs the job
class TrackerSyncJobTests {

    @MockitoBean
    TicketTracker tracker;

    @MockitoBean
    UserDirectory users;

    @Autowired
    TrackerSyncJob job;

    @Autowired
    SupportService support;

    @Autowired
    JdbcTemplate jdbc;

    private final UUID ada = UUID.randomUUID();

    @BeforeEach
    void cleanUp() {
        jdbc.update("delete from support.tickets");                      // each test sees only its own tickets
        given(users.findByIds(any())).willReturn(Map.of(ada, new UserSummary(ada, "alovelace", "Ada Lovelace", null)));
    }

    private TicketView ticket(String title) {
        return support.submit(new Actor(ada, false), new NewTicket(TicketType.BUG, title, "Steps: ~~~ click ~~~", "/c/chess", "Firefox/140.0"));
    }

    private Map<String, Object> row(long number) {
        return jdbc.queryForMap("select status, tracker_issue, tracker_attempts, tracker_next_attempt_at, tracker_last_error "
            + "from support.tickets where number = ?", number);
    }

    private void dueNow(long number) {
        jdbc.update("update support.tickets set tracker_next_attempt_at = now() - interval '1 second' where number = ?", number);
    }

    @Test
    void aTicketBecomesOneIssueWithItsContext() {
        TicketView t = ticket("Login button does nothing");
        given(tracker.createIssue(any())).willReturn(42);

        job.sendTickets();
        job.sendTickets();

        ArgumentCaptor<NewIssue> sent = ArgumentCaptor.forClass(NewIssue.class);
        verify(tracker).createIssue(sent.capture());                     // once
        NewIssue issue = sent.getValue();
        assertThat(issue.title()).isEqualTo(t.ref() + " Login button does nothing");
        assertThat(issue.type()).isEqualTo(TicketType.BUG);
        assertThat(issue.retry()).isFalse();
        assertThat(issue.body())
            .contains("**" + t.ref() + "** · Bug · sent from the app by **Ada Lovelace** (`alovelace`)")
            .contains("Page: https://clubroom.test/c/chess")
            .contains("Browser: `Firefox/140.0`")
            .contains("~~~~text\nSteps: ~~~ click ~~~\n~~~~\n");          // a longer fence than the text's
        assertThat(row(t.number())).containsEntry("status", "OPEN").containsEntry("tracker_issue", 42);
    }

    @Test
    void whileTheTrackerIsDownTicketsWaitLongerEachTimeAndAreNeverDropped() {
        TicketView first = ticket("First");
        TicketView second = ticket("Second");
        given(tracker.createIssue(any())).willThrow(new TrackerException("Forgejo unreachable: Connection refused", null));

        Instant before = Instant.now();
        job.sendTickets();
        assertThat(row(first.number())).containsEntry("tracker_attempts", 1)
            .containsEntry("tracker_last_error", "Forgejo unreachable: Connection refused");
        assertThat(((Timestamp) row(first.number()).get("tracker_next_attempt_at")).toInstant())
            .isBetween(before.plus(Duration.ofMinutes(1)), Instant.now().plus(Duration.ofMinutes(1)));
        assertThat(row(second.number())).containsEntry("tracker_attempts", 0);   // the run stopped at the first failure

        for (int attempt = 2; attempt <= 9; attempt++) {
            dueNow(first.number());
            jdbc.update("update support.tickets set tracker_next_attempt_at = now() + interval '1 hour' where number = ?", second.number());
            job.sendTickets();
        }
        assertThat(row(first.number())).containsEntry("tracker_attempts", 9).containsEntry("status", "RECEIVED");

        willReturn(7).given(tracker).createIssue(any());                 // back up
        dueNow(first.number());
        job.sendTickets();
        ArgumentCaptor<NewIssue> attempts = ArgumentCaptor.forClass(NewIssue.class);
        verify(tracker, times(10)).createIssue(attempts.capture());
        assertThat(attempts.getAllValues()).extracting(NewIssue::retry)   // after a failure, look before creating
            .containsExactly(false, true, true, true, true, true, true, true, true, true);
        assertThat(row(first.number())).containsEntry("tracker_issue", 7).containsEntry("tracker_last_error", null);
    }

    @Test
    void aTicketNotDueYetWaits() {
        TicketView t = ticket("Later");
        jdbc.update("update support.tickets set tracker_next_attempt_at = now() + interval '5 minutes' where number = ?", t.number());

        job.sendTickets();

        verify(tracker, never()).createIssue(any());
        assertThat(List.of(row(t.number()).get("status"))).containsExactly("RECEIVED");
    }
}
