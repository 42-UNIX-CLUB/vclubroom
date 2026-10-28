package pt.vclubroom.calendar.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pt.vclubroom.TestRequests.user;
import static pt.vclubroom.TestRequests.xsrf;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import pt.vclubroom.TestRequests;
import pt.vclubroom.TestcontainersConfiguration;
import pt.vclubroom.calendar.domain.EventReminder;
import pt.vclubroom.calendar.persistence.EventReminderRepository;
import pt.vclubroom.club.api.JoinPolicy;
import pt.vclubroom.club.application.ClubService;
import pt.vclubroom.club.application.CreateClubCommand;
import pt.vclubroom.club.persistence.ClubRepository;
import pt.vclubroom.membership.api.ClubAuthorization;
import pt.vclubroom.shared.security.Actor;
import pt.vclubroom.space.persistence.SpaceRepository;

/**
 * Event reminders (E11-S04): planned with the event, planned again when it moves, dropped when it is cancelled, and
 * sent once to the people who answered GOING or INTERESTED. The test runs the scheduler itself (the scheduled run is
 * set to an hour in the test properties).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ReminderIntegrationTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    ClubService clubs;

    @Autowired
    ClubRepository clubRepository;

    @Autowired
    ClubAuthorization authorization;

    @Autowired
    SpaceRepository spaces;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    EventReminderRepository reminders;

    @Autowired
    ReminderScheduler scheduler;

    @Autowired
    JdbcTemplate jdbc;

    private final UUID founder = UUID.randomUUID();
    private final UUID ada = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private UUID clubId;
    private UUID general;

    @BeforeEach
    void anOpenClubWithTwoMembers() throws Exception {
        clubId = clubs.requestCreation(new Actor(founder, false),
            new CreateClubCommand("Go " + TestRequests.suffix(), null, null, "Games", List.of())).id();
        clubs.approve(new Actor(UUID.randomUUID(), true), clubId);
        await().atMost(Duration.ofSeconds(10)).until(() ->
            authorization.isOwner(clubId, founder) && !spaces.findByClubIdOrderByPositionAsc(clubId).isEmpty());
        general = spaces.findByClubIdOrderByPositionAsc(clubId).getFirst().id();
        tx.executeWithoutResult(s -> clubRepository.findById(clubId).orElseThrow().changeJoinPolicy(JoinPolicy.OPEN, Instant.now()));
        for (UUID member : List.of(ada, bob)) {
            send(post("/api/clubs/{id}/join", clubId), member, "{}").andExpect(status().isCreated());
        }
    }

    private ResultActions send(MockHttpServletRequestBuilder request, UUID as, String json) throws Exception {
        return mvc.perform(request.with(user(as, false)).with(xsrf(mvc)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String json(Instant startsAt) {
        return """
               {"spaceId":"%s","title":"Go night","location":"Cluster 1","startsAt":"%s","endsAt":"%s"}"""
            .formatted(general, startsAt, startsAt.plus(Duration.ofHours(2)));
    }

    private String schedule(Instant startsAt) throws Exception {
        return JsonPath.read(send(post("/api/clubs/{id}/events", clubId), founder, json(startsAt))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private List<String> planned(String eventId) {
        return reminders.findByEventIdOrderByRemindAtAsc(UUID.fromString(eventId)).stream()
            .filter(r -> r.sentAt() == null).map(r -> r.kind() + "@" + r.remindAt()).toList();
    }

    private static Instant inHours(long hours) {
        return Instant.now().plus(Duration.ofHours(hours)).truncatedTo(ChronoUnit.SECONDS);
    }

    @Test
    void remindersFollowTheEventsStart() throws Exception {
        Instant start = inHours(48);
        String id = schedule(start);
        assertThat(planned(id)).containsExactly("DAY_BEFORE@" + start.minus(Duration.ofHours(24)), "HOUR_BEFORE@" + start.minus(Duration.ofHours(1)));

        Instant sooner = inHours(5);                                                  // too close for the day-before one
        send(patch("/api/events/{id}", id), founder, json(sooner)).andExpect(status().isOk());
        assertThat(planned(id)).containsExactly("HOUR_BEFORE@" + sooner.minus(Duration.ofHours(1)));

        send(post("/api/events/{id}/cancel", id), founder, "{\"reason\":\"Room taken\"}").andExpect(status().isOk());
        assertThat(planned(id)).isEmpty();
    }

    @Test
    void aDueReminderReachesThoseWhoAnsweredOnce() throws Exception {
        String id = schedule(inHours(30));
        send(put("/api/events/{id}/rsvp", id), ada, "{\"status\":\"GOING\"}").andExpect(status().isOk());
        jdbc.update("update calendar.event_reminders set remind_at = now() - interval '1 minute' where event_id = ?::uuid and kind = 'DAY_BEFORE'", id);

        scheduler.sendDueReminders();
        scheduler.sendDueReminders();                                                 // already sent: nothing more

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> mvc.perform(get("/api/notifications").with(user(ada, false)))
            .andExpect(jsonPath("$.items[*].title", hasItem("Tomorrow: Go night"))));
        assertThat(jdbc.queryForObject("select count(*) from notification.notifications where recipient_id = ? and type = 'EVENT_REMINDER'",
            Integer.class, ada)).isOne();
        mvc.perform(get("/api/notifications").with(user(bob, false)))                // didn't answer
            .andExpect(jsonPath("$.items[*].title", not(hasItem("Tomorrow: Go night"))));
        assertThat(reminders.findByEventIdOrderByRemindAtAsc(UUID.fromString(id))).extracting(EventReminder::sentAt)
            .satisfiesExactly(sent -> assertThat(sent).isNotNull(), unsent -> assertThat(unsent).isNull());
    }

    @Test
    void aReminderOfACancelledEventGoesNowhere() throws Exception {
        String id = schedule(inHours(30));
        send(put("/api/events/{id}/rsvp", id), ada, "{\"status\":\"INTERESTED\"}").andExpect(status().isOk());
        jdbc.update("update calendar.event_reminders set remind_at = now() - interval '1 minute' where event_id = ?::uuid", id);
        jdbc.update("update calendar.events set status = 'CANCELLED' where id = ?::uuid", id);       // as when its club is archived

        scheduler.sendDueReminders();

        assertThat(jdbc.queryForObject("select count(*) from calendar.event_reminders where event_id = ?::uuid and sent_at is null",
            Integer.class, id)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from notification.notifications where recipient_id = ? and type = 'EVENT_REMINDER'",
            Integer.class, ada)).isZero();
    }
}
