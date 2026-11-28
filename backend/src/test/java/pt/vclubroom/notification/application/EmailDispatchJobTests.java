package pt.vclubroom.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pt.vclubroom.TestcontainersConfiguration;
import pt.vclubroom.calendar.api.RsvpQuery;
import pt.vclubroom.club.api.ClubCatalog;
import pt.vclubroom.identity.api.UserContact;
import pt.vclubroom.identity.api.UserDirectory;
import pt.vclubroom.membership.api.ClubAuthorization;
import pt.vclubroom.realtime.api.RealtimePublisher;
import pt.vclubroom.space.api.SpaceAccessPolicy;
import pt.vclubroom.space.api.SpaceAudience;

/** The email outbox on its own (E12-S03): what is sent, retried with backoff (R-27), or given up. The sender is mocked. */
@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {"app.mail.poll-interval=PT1H", "app.public-url=https://clubroom.test/",   // the test runs the job itself
    "app.mail.support-from=Virtual Clubroom Support <support@clubroom.test>"})
class EmailDispatchJobTests {

    @MockitoBean
    EmailSender emailSender;

    @MockitoBean
    UserDirectory users;

    @MockitoBean
    SpaceAudience audience;

    @MockitoBean
    SpaceAccessPolicy spaceAccess;

    @MockitoBean
    ClubAuthorization authorization;

    @MockitoBean
    ClubCatalog clubs;

    @MockitoBean
    RealtimePublisher realtime;

    @MockitoBean
    RsvpQuery rsvps;

    @Autowired
    EmailDispatchJob job;

    @Autowired
    JdbcTemplate jdbc;

    /** A notification as the dispatcher writes it, for someone with an address. */
    private UUID notification(String email, boolean emailRequested) {
        return notification(email, emailRequested, "CLUB_APPROVED", "Chess is approved", "Welcome aboard", "/c/chess");
    }

    private UUID notification(String email, boolean emailRequested, String type, String title, String body, String link) {
        UUID recipient = UUID.randomUUID();
        given(users.contactOf(recipient)).willReturn(Optional.ofNullable(email).map(e -> new UserContact(recipient, e, "Ada", true)));
        UUID id = UUID.randomUUID();
        jdbc.update("""
            insert into notification.notifications (id, recipient_id, type, title, body, link, dedup_key, in_app, email_requested, created_at)
            values (?, ?, ?, ?, ?, ?, ?, true, ?, now())""",
            id, recipient, type, title, body, link, "TEST:" + id, emailRequested);
        return id;
    }

    private Map<String, Object> row(UUID id) {
        return jdbc.queryForMap("select email_requested, email_sent_at, email_attempts, email_next_attempt_at, email_last_error "
            + "from notification.notifications where id = ?", id);
    }

    private void dueNow(UUID id) {
        jdbc.update("update notification.notifications set email_next_attempt_at = now() - interval '1 second' where id = ?", id);
    }

    @Test
    void aRequestedEmailIsSentOnceWithItsLinkAndTheSettings() {
        UUID id = notification("ada@example.test", true);
        UUID inAppOnly = notification("bob@example.test", false);

        job.sendPendingEmails();
        job.sendPendingEmails();

        verify(emailSender, times(1)).send(new OutgoingEmail("ada@example.test", "[Virtual Clubroom] Chess is approved", """
            Welcome aboard

            Open: https://clubroom.test/c/chess

            --
            Choose which emails you get: https://clubroom.test/settings
            """));
        verify(emailSender, never()).send(argThat(e -> e.to().equals("bob@example.test")));
        assertThat(row(id).get("email_sent_at")).isNotNull();
        assertThat(row(inAppOnly).get("email_sent_at")).isNull();
    }

    @Test
    void supportEmailsComeFromTheSupportAddressAndSaySo() {
        notification("ada@example.test", true, "SUPPORT_TICKET_REPLIED", "T-17: the team answered “Login”", "Which browser?", "/support/17");

        job.sendPendingEmails();

        verify(emailSender).send(new OutgoingEmail("ada@example.test", "[Virtual Clubroom Support] T-17: the team answered “Login”", """
            Virtual Clubroom Support
            ========================

            Which browser?

            Your ticket: https://clubroom.test/support/17

            --
            You get this email because you sent this ticket. Please answer on the ticket's page:
            replies to this email don't reach the team.
            Choose which emails you get: https://clubroom.test/settings
            """, "Virtual Clubroom Support <support@clubroom.test>"));
    }

    @Test
    void failuresBackOffAndStopAfterFiveAttempts() {
        UUID id = notification("down@example.test", true);
        willThrow(new EmailDeliveryException("SMTP delivery failed: 421 try later", null))
            .given(emailSender).send(argThat(e -> e != null && e.to().equals("down@example.test")));

        Instant before = Instant.now();
        job.sendPendingEmails();
        Map<String, Object> first = row(id);
        assertThat(first.get("email_attempts")).isEqualTo(1);
        assertThat(first.get("email_last_error")).isEqualTo("SMTP delivery failed: 421 try later");
        assertThat(((Timestamp) first.get("email_next_attempt_at")).toInstant())
            .isBetween(before.plus(Duration.ofMinutes(1)), Instant.now().plus(Duration.ofMinutes(1)));

        job.sendPendingEmails();                                                    // not due yet
        assertThat(row(id).get("email_attempts")).isEqualTo(1);

        dueNow(id);
        Instant second = Instant.now();
        job.sendPendingEmails();
        assertThat(((Timestamp) row(id).get("email_next_attempt_at")).toInstant())
            .isBetween(second.plus(Duration.ofMinutes(2)), Instant.now().plus(Duration.ofMinutes(2)));

        for (int attempt = 3; attempt <= 6; attempt++) {
            dueNow(id);
            job.sendPendingEmails();
        }
        assertThat(row(id).get("email_attempts")).isEqualTo(EmailDispatchJob.MAX_ATTEMPTS);
        assertThat(row(id).get("email_sent_at")).isNull();
        verify(emailSender, times(EmailDispatchJob.MAX_ATTEMPTS)).send(argThat(e -> e != null && e.to().equals("down@example.test")));
    }

    @Test
    void nobodyToWriteToMeansNoMoreTries() {
        UUID noAddress = notification(null, true);
        UUID gone = notification("gone@example.test", true);
        UUID goneUser = jdbc.queryForObject("select recipient_id from notification.notifications where id = ?", UUID.class, gone);
        given(users.contactOf(goneUser)).willReturn(Optional.of(new UserContact(goneUser, "gone@example.test", "Gone", false)));

        job.sendPendingEmails();

        assertThat(row(noAddress).get("email_requested")).isEqualTo(false);
        assertThat(row(gone).get("email_requested")).isEqualTo(false);
        verify(emailSender, never()).send(argThat(e -> e != null && e.to().equals("gone@example.test")));
    }
}
