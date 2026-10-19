package pt.vclubroom.calendar;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pt.vclubroom.TestRequests.user;
import static pt.vclubroom.TestRequests.xsrf;

import com.jayway.jsonpath.JsonPath;
import java.sql.Timestamp;
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
import pt.vclubroom.club.api.JoinPolicy;
import pt.vclubroom.club.application.ClubService;
import pt.vclubroom.club.application.CreateClubCommand;
import pt.vclubroom.club.persistence.ClubRepository;
import pt.vclubroom.identity.api.PlatformRole;
import pt.vclubroom.identity.domain.User;
import pt.vclubroom.identity.persistence.UserRepository;
import pt.vclubroom.membership.api.ClubAuthorization;
import pt.vclubroom.shared.security.Actor;
import pt.vclubroom.space.domain.AccessRule;
import pt.vclubroom.space.domain.Space;
import pt.vclubroom.space.persistence.SpaceRepository;

/** Going, interested, not going (E11-S03, BR-22): counts, who is coming, the calendars, and the notices that follow. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RsvpIntegrationTests {

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
    UserRepository users;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcTemplate jdbc;

    private final Instant start = Instant.now().plus(Duration.ofDays(3)).truncatedTo(ChronoUnit.SECONDS);
    private UUID founder;
    private UUID ada;
    private UUID bob;
    private UUID clubId;
    private UUID general;
    private UUID boardRoom;

    private UUID person(String name) {
        String login = name.toLowerCase().replace(' ', '.') + "-" + TestRequests.suffix();
        return users.save(User.createLocal(login, name, login + "@example.test", "{noop}x", PlatformRole.USER, Instant.now())).id();
    }

    private ResultActions send(MockHttpServletRequestBuilder request, UUID as, String json) throws Exception {
        return mvc.perform(request.with(user(as, false)).with(xsrf(mvc)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions rsvp(UUID as, String eventId, String status) throws Exception {
        return send(put("/api/events/{id}/rsvp", eventId), as, "{\"status\":\"" + status + "\"}");
    }

    private String schedule(UUID spaceId) throws Exception {
        String json = """
            {"spaceId":"%s","title":"Blitz night","startsAt":"%s","endsAt":"%s"}""".formatted(spaceId, start, start.plus(Duration.ofHours(3)));
        return JsonPath.read(send(post("/api/clubs/{id}/events", clubId), founder, json)
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    /** An OPEN club with two members, a PUBLIC space (General) and a members-only one. */
    @BeforeEach
    void aClubWithMembers() throws Exception {
        founder = person("Dan Founder");
        ada = person("Ada Lopes");
        bob = person("Bob Martins");
        clubId = clubs.requestCreation(new Actor(founder, false),
            new CreateClubCommand("Chess " + TestRequests.suffix(), null, null, "Games", List.of())).id();
        clubs.approve(new Actor(UUID.randomUUID(), true), clubId);
        await().atMost(Duration.ofSeconds(10)).until(() ->
            authorization.isOwner(clubId, founder) && !spaces.findByClubIdOrderByPositionAsc(clubId).isEmpty());
        general = spaces.findByClubIdOrderByPositionAsc(clubId).getFirst().id();
        boardRoom = spaces.save(Space.create(clubId, "Board room", null, 1, AccessRule.clubMembers(), AccessRule.clubMembers(),
            Instant.now())).id();
        tx.executeWithoutResult(s -> clubRepository.findById(clubId).orElseThrow().changeJoinPolicy(JoinPolicy.OPEN, Instant.now()));
        for (UUID who : List.of(ada, bob)) {
            send(post("/api/clubs/{id}/join", clubId), who, "{}").andExpect(status().isCreated());
        }
    }

    @Test
    void answersAreCountedAtOnceChangedAndWithdrawn() throws Exception {
        String id = schedule(general);

        rsvp(ada, id, "GOING").andExpect(status().isOk())
            .andExpect(jsonPath("$.going").value(1)).andExpect(jsonPath("$.myRsvp").value("GOING")).andExpect(jsonPath("$.canRsvp").value(true));
        rsvp(bob, id, "INTERESTED").andExpect(jsonPath("$.going").value(1)).andExpect(jsonPath("$.interested").value(1));
        rsvp(bob, id, "GOING").andExpect(jsonPath("$.going").value(2)).andExpect(jsonPath("$.interested").value(0));
        rsvp(founder, id, "NOT_GOING").andExpect(jsonPath("$.going").value(2)).andExpect(jsonPath("$.myRsvp").value("NOT_GOING"));

        mvc.perform(get("/api/events/{id}/attendees", id).with(user(founder, false)))
            .andExpect(jsonPath("$[*].user.displayName", contains("Ada Lopes", "Bob Martins")));              // going and interested
        mvc.perform(get("/api/events/{id}/attendees?status=NOT_GOING", id).with(user(ada, false)))
            .andExpect(jsonPath("$[*].user.displayName", contains("Dan Founder")));
        String from = start.minus(Duration.ofDays(1)).toString();
        String to = start.plus(Duration.ofDays(1)).toString();
        mvc.perform(get("/api/clubs/{id}/events", clubId).param("from", from).param("to", to).with(user(ada, false)))
            .andExpect(jsonPath("$[0].going").value(2)).andExpect(jsonPath("$[0].myRsvp").value("GOING"));

        send(delete("/api/events/{id}/rsvp", id), bob, "").andExpect(status().isOk())
            .andExpect(jsonPath("$.going").value(1)).andExpect(jsonPath("$.myRsvp").doesNotExist());
        send(put("/api/events/{id}/rsvp", id), bob, "{}").andExpect(status().isBadRequest());
    }

    @Test
    void onlyReadersAnswerAndOnlyBeforeTheEnd() throws Exception {
        String membersOnly = schedule(boardRoom);
        rsvp(UUID.randomUUID(), membersOnly, "GOING").andExpect(status().isForbidden());           // cannot read the space
        mvc.perform(get("/api/events/{id}/attendees", membersOnly).with(user(UUID.randomUUID(), false))).andExpect(status().isForbidden());

        String past = schedule(general);
        jdbc.update("update calendar.events set starts_at = ?, ends_at = ? where id = ?::uuid",
            Timestamp.from(Instant.now().minus(Duration.ofHours(3))), Timestamp.from(Instant.now().minus(Duration.ofHours(1))), past);
        rsvp(ada, past, "GOING").andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("RSVP_CLOSED"));
        mvc.perform(get("/api/events/{id}", past).with(user(ada, false))).andExpect(jsonPath("$.canRsvp").value(false));

        String cancelled = schedule(general);
        send(post("/api/events/{id}/cancel", cancelled), founder, "{}").andExpect(status().isOk());
        rsvp(ada, cancelled, "GOING").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void peopleWhoAnsweredHearAboutChangesAndCancellations() throws Exception {
        String id = schedule(general);
        rsvp(ada, id, "GOING");
        rsvp(bob, id, "NOT_GOING");

        send(patch("/api/events/{id}", id), founder, """
            {"title":"Blitz night","startsAt":"%s","endsAt":"%s"}""".formatted(start.plus(Duration.ofHours(1)), start.plus(Duration.ofHours(4))))
            .andExpect(status().isOk());
        send(post("/api/events/{id}/cancel", id), founder, "{\"reason\":\"Room unavailable\"}").andExpect(status().isOk());

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> mvc.perform(get("/api/notifications").with(user(ada, false)))
            .andExpect(jsonPath("$.items[*].type", hasItem("EVENT_UPDATED")))
            .andExpect(jsonPath("$.items[0].type").value("EVENT_CANCELLED"))
            .andExpect(jsonPath("$.items[0].body").value("Room unavailable")));
        mvc.perform(get("/api/notifications").with(user(bob, false)))                             // not going: only the new event
            .andExpect(jsonPath("$.items[*].type", not(hasItem("EVENT_CANCELLED"))))
            .andExpect(jsonPath("$.items[*].type", not(hasItem("EVENT_UPDATED"))));
        mvc.perform(get("/api/events/{id}/attendees?status=GOING", id).with(user(founder, false)))
            .andExpect(jsonPath("$[*].user.displayName", contains("Ada Lopes")));
        mvc.perform(get("/api/events/{id}/attendees?status=INTERESTED", id).with(user(founder, false)))
            .andExpect(jsonPath("$", empty()));
    }
}
