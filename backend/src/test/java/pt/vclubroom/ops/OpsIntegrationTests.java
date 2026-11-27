package pt.vclubroom.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pt.vclubroom.TestRequests.user;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import pt.vclubroom.TestcontainersConfiguration;
import pt.vclubroom.ops.application.OpsService;

/** The operations report (E15-S04, E17-S04): the event outbox per listener, the email outbox and the tracker sync, for admins only. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpsIntegrationTests {

    private static final String LISTENER = "pt.vclubroom.test.StuckListener.on(pt.vclubroom.test.Happened)";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private final UUID failedEmail = UUID.randomUUID();
    private final UUID waitingTicket = UUID.randomUUID();

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from public.event_publication where listener_id = ?", LISTENER);   // never retried by others
        jdbc.update("delete from notification.notifications where id = ?", failedEmail);
        jdbc.update("delete from support.tickets where id = ?", waitingTicket);
    }

    @Test
    void adminsSeeStuckListenersAndTheEmailOutbox() throws Exception {
        jdbc.update("""
            insert into public.event_publication (id, listener_id, event_type, serialized_event, publication_date)
            values (gen_random_uuid(), ?, 'pt.vclubroom.test.Happened', '{}', now())""", LISTENER);   // too recent to be retried
        jdbc.update("""
            insert into notification.notifications (id, recipient_id, type, title, dedup_key, in_app, email_requested, email_attempts,
              email_next_attempt_at, email_last_error, created_at)
            values (?, gen_random_uuid(), 'CLUB_APPROVED', 'Chess is approved', ?, true, true, 5, now(), 'SMTP delivery failed: 550 no such user', now())""",
            failedEmail, "TEST:" + failedEmail);

        mvc.perform(get("/api/admin/ops").with(user(UUID.randomUUID(), true)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.outbox[?(@.listener == 'StuckListener.on')].eventType").value(hasItem("Happened")))
            .andExpect(jsonPath("$.outbox[?(@.listener == 'StuckListener.on')].count").value(hasItem(1)))
            .andExpect(jsonPath("$.emails.failed").value(greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.emails.lastError").value("SMTP delivery failed: 550 no such user"))
            .andExpect(jsonPath("$.refusedApiRequests").value(greaterThanOrEqualTo(0)))   // E20-S03
            .andExpect(jsonPath("$.measuredAt").isNotEmpty());
        mvc.perform(get("/api/admin/ops").with(user(UUID.randomUUID(), false))).andExpect(status().isForbidden());
    }

    @Test
    void adminsSeeTicketsTheTrackerDoesNotHaveYet() throws Exception {
        jdbc.update("""
            insert into support.tickets (id, number, reporter_id, type, title, description, status, tracker_attempts,
              tracker_next_attempt_at, tracker_last_error, created_at, updated_at)
            values (?, nextval('support.ticket_number_seq'), gen_random_uuid(), 'BUG', 'Broken', 'It is', 'RECEIVED', 3,
              now() + interval '1 hour', 'Forgejo unreachable: Connection refused', now() - interval '20 minutes', now())""",
            waitingTicket);

        mvc.perform(get("/api/admin/ops").with(user(UUID.randomUUID(), true)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tickets.unsentTickets").value(greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.tickets.oldestUnsent").isNotEmpty())
            .andExpect(jsonPath("$.tickets.lastRead").isNotEmpty())
            .andExpect(jsonPath("$.tickets.lastError").value("Forgejo unreachable: Connection refused"));
    }

    @Test
    void listenerNamesAreShortened() {
        assertThat(OpsService.shortName(LISTENER)).isEqualTo("StuckListener.on");
        assertThat(OpsService.shortName("pt.vclubroom.test.Happened")).isEqualTo("Happened");
    }
}
