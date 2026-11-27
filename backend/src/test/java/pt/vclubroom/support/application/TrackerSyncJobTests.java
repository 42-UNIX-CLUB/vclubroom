package pt.vclubroom.support.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.AssertablePublishedEvents;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pt.vclubroom.TestcontainersConfiguration;
import pt.vclubroom.identity.api.UserDirectory;
import pt.vclubroom.identity.api.UserSummary;
import pt.vclubroom.shared.security.Actor;
import pt.vclubroom.support.domain.LinkedWork;
import pt.vclubroom.support.domain.TicketStatus;
import pt.vclubroom.support.domain.TicketType;
import pt.vclubroom.support.domain.WorkState;
import pt.vclubroom.support.events.TicketReplied;
import pt.vclubroom.support.events.TicketStatusChanged;

/**
 * The tracker sync on its own (E17-S02, E17-S03, E18-S03): tickets and replies sent once, retried with backoff, never
 * given up; the tracker's changes and linked work read once. The tracker is mocked.
 */
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
        jdbc.update("delete from support.ticket_links");                  // each test sees only its own tickets
        jdbc.update("delete from support.ticket_messages");
        jdbc.update("delete from support.tickets");
        jdbc.update("update support.tracker_cursor set read_until = now(), last_error = null");
        UserSummary summary = new UserSummary(ada, "alovelace", "Ada Lovelace", null);
        given(users.findByIds(any())).willReturn(Map.of(ada, summary));
        given(users.findById(ada)).willReturn(Optional.of(summary));
    }

    private TicketView ticket(String title) {
        return support.submit(new Actor(ada, false), new NewTicket(TicketType.BUG, title, "Steps: ~~~ click ~~~", "/c/chess", "Firefox/140.0"));
    }

    /** As if TrackerSyncJob had sent it: issue number set, open. */
    private void inTracker(TicketView t, int issue) {
        jdbc.update("update support.tickets set tracker_issue = ?, status = 'OPEN' where number = ?", issue, t.number());
    }

    private Map<String, Object> row(long number) {
        return jdbc.queryForMap("select status, tracker_issue, tracker_attempts, tracker_next_attempt_at, tracker_last_error "
            + "from support.tickets where number = ?", number);
    }

    private List<Map<String, Object>> messages(long number) {
        return jdbc.queryForList("select author, body, tracker_comment_id, tracker_attempts from support.ticket_messages "
            + "where ticket_id = (select id from support.tickets where number = ?) order by created_at", number);
    }

    private Map<String, Object> cursor() {
        return jdbc.queryForMap("select read_until, last_error from support.tracker_cursor");
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
            .contains("~~~~text\nSteps: ~~~ click ~~~\n~~~~\n")           // a longer fence than the text's
            .contains("start a comment with `/reply`");
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
        assertThat(row(t.number())).containsEntry("status", "RECEIVED");
    }

    @Test
    void aReplyGoesToTheTrackerOnceItsIssueExistsAndIsRetried() {
        TicketView t = ticket("Login button does nothing");
        support.reply(new Actor(ada, false), t.number(), "Firefox 140, on Linux");

        job.sendReplies();
        verify(tracker, never()).addReporterReply(any(Integer.class), any());   // no issue yet

        inTracker(t, 5);
        given(tracker.addReporterReply(eq(5), any())).willThrow(new TrackerException("Forgejo answered 503 Service Unavailable", null));
        job.sendReplies();
        assertThat(messages(t.number()).getFirst()).containsEntry("tracker_attempts", 1).containsEntry("tracker_comment_id", null);

        willReturn(301L).given(tracker).addReporterReply(eq(5), any());
        jdbc.update("update support.ticket_messages set tracker_next_attempt_at = now() - interval '1 second'");
        job.sendReplies();
        job.sendReplies();

        ArgumentCaptor<ReporterReply> sent = ArgumentCaptor.forClass(ReporterReply.class);
        verify(tracker, times(2)).addReporterReply(eq(5), sent.capture());
        assertThat(sent.getAllValues()).extracting(ReporterReply::retry).containsExactly(false, true);
        assertThat(sent.getValue().body()).startsWith("**Ada Lovelace** replied in the app:").contains("Firefox 140, on Linux");
        assertThat(messages(t.number()).getFirst()).containsEntry("tracker_comment_id", 301L);
    }

    @Test
    void theTeamsAnswersAndStatusChangesReachTheReporterOnce(AssertablePublishedEvents events) {
        TicketView t = ticket("Login button does nothing");
        inTracker(t, 5);
        given(tracker.changesSince(any())).willReturn(new TrackerChanges(
            List.of(new TrackerChanges.Issue(5, TicketStatus.WAITING_ON_REPORTER, List.of()), new TrackerChanges.Issue(99, TicketStatus.RESOLVED, List.of())),
            List.of(new TrackerChanges.Comment(201, 5, false, "Reproduced on Firefox"),             // between the team
                new TrackerChanges.Comment(202, 5, false, "/reply Which browser do you use?"),
                new TrackerChanges.Comment(203, 5, true, "**Ada Lovelace** replied in the app: …"),  // the app's own copy
                new TrackerChanges.Comment(204, 99, false, "/reply On an issue that isn't a ticket")), List.of()));

        Instant before = Instant.now();
        job.readTrackerChanges();
        job.readTrackerChanges();                                         // the same changes again (the overlap)

        assertThat(row(t.number())).containsEntry("status", "WAITING_ON_REPORTER");
        assertThat(messages(t.number())).hasSize(1).first()
            .satisfies(m -> assertThat(m).containsEntry("author", "TEAM").containsEntry("body", "Which browser do you use?")
                .containsEntry("tracker_comment_id", 202L));
        assertThat(events.ofType(TicketStatusChanged.class)).hasSize(1).first().satisfies(e -> {
            assertThat(e.statusLabel()).isEqualTo("Waiting for you");
            assertThat(e.reporterId()).isEqualTo(ada);
        });
        assertThat(events.ofType(TicketReplied.class)).hasSize(1);

        ArgumentCaptor<Instant> since = ArgumentCaptor.forClass(Instant.class);
        verify(tracker, times(2)).changesSince(since.capture());
        assertThat(since.getAllValues().getFirst()).isBefore(before.minus(Duration.ofMinutes(2)).plusSeconds(1));
        assertThat(((Timestamp) cursor().get("read_until")).toInstant()).isAfterOrEqualTo(before.minusSeconds(1));
    }

    @Test
    void aWaitingLabelTheReporterHasAnsweredIsOutOfDate(AssertablePublishedEvents events) {
        TicketView t = ticket("Login button does nothing");
        inTracker(t, 5);
        jdbc.update("update support.tickets set status = 'WAITING_ON_REPORTER' where number = ?", t.number());
        support.reply(new Actor(ada, false), t.number(), "Firefox 140");     // open again, the label goes with the reply
        given(tracker.changesSince(any())).willReturn(
            new TrackerChanges(List.of(new TrackerChanges.Issue(5, TicketStatus.WAITING_ON_REPORTER, List.of())), List.of(), List.of()),
            new TrackerChanges(List.of(new TrackerChanges.Issue(5, TicketStatus.OPEN, List.of())), List.of(), List.of()),
            new TrackerChanges(List.of(new TrackerChanges.Issue(5, TicketStatus.WAITING_ON_REPORTER, List.of())),
                List.of(new TrackerChanges.Comment(301, 5, false, "/reply And which version of Linux?")), List.of()));

        job.readTrackerChanges();                                         // the reply hasn't reached the tracker yet
        assertThat(row(t.number())).containsEntry("status", "OPEN");
        job.readTrackerChanges();                                         // now it has
        assertThat(row(t.number())).containsEntry("status", "OPEN");
        assertThat(events.ofType(TicketStatusChanged.class)).isEmpty();

        job.readTrackerChanges();                                         // a new question: waiting for them again
        assertThat(row(t.number())).containsEntry("status", "WAITING_ON_REPORTER");
        assertThat(events.ofType(TicketStatusChanged.class)).hasSize(1);
    }

    @Test
    void linkedWorkFollowsTheTracker() {
        TicketView t = ticket("Pinned posts disappear");
        inTracker(t, 5);
        LinkedWork planned = new LinkedWork(140, "E09-S04 · Edit, delete, pin and moderate", WorkState.PLANNED, "v1.2.0", false);
        LinkedWork elsewhere = new LinkedWork(141, "E12-S01 · In-app notifications", WorkState.PLANNED, null, false);
        given(tracker.changesSince(any())).willReturn(
            new TrackerChanges(List.of(new TrackerChanges.Issue(5, TicketStatus.OPEN, List.of(planned))), List.of(), List.of()),
            new TrackerChanges(List.of(), List.of(), List.of(           // the development issue moved on, alone
                new LinkedWork(140, "E09-S04 · Edit, delete, pin and moderate", WorkState.DONE, "v1.2.0", true), elsewhere)),
            new TrackerChanges(List.of(new TrackerChanges.Issue(5, TicketStatus.OPEN, List.of())), List.of(), List.of()));   // unlinked

        job.readTrackerChanges();
        assertThat(links(t.number())).containsExactly(Map.of("issue", 140, "state", "PLANNED", "released", false));
        job.readTrackerChanges();
        assertThat(links(t.number())).containsExactly(Map.of("issue", 140, "state", "DONE", "released", true));
        job.readTrackerChanges();
        assertThat(links(t.number())).isEmpty();
    }

    private List<Map<String, Object>> links(long number) {
        return jdbc.queryForList("select issue, state, released from support.ticket_links "
            + "where ticket_id = (select id from support.tickets where number = ?) order by issue", number);
    }

    @Test
    void aFailedReadKeepsTheCursorAndSaysWhy() {
        Object start = cursor().get("read_until");
        given(tracker.changesSince(any())).willThrow(new TrackerException("Forgejo answered 502 Bad Gateway", null));

        job.readTrackerChanges();

        assertThat(cursor()).containsEntry("read_until", start).containsEntry("last_error", "Forgejo answered 502 Bad Gateway");
        willReturn(new TrackerChanges(List.of(), List.of(), List.of())).given(tracker).changesSince(any());
        job.readTrackerChanges();
        assertThat(cursor()).containsEntry("last_error", null);
    }
}
