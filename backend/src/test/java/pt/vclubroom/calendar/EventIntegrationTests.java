package pt.vclubroom.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import pt.vclubroom.TestRequests;
import pt.vclubroom.TestcontainersConfiguration;
import pt.vclubroom.calendar.events.EventCancelled;
import pt.vclubroom.calendar.events.EventScheduled;
import pt.vclubroom.calendar.events.EventUpdated;
import pt.vclubroom.club.api.JoinPolicy;
import pt.vclubroom.club.application.ClubService;
import pt.vclubroom.club.application.CreateClubCommand;
import pt.vclubroom.club.persistence.ClubRepository;
import pt.vclubroom.membership.api.ClubAuthorization;
import pt.vclubroom.shared.security.Actor;
import pt.vclubroom.space.domain.AccessRule;
import pt.vclubroom.space.domain.Space;
import pt.vclubroom.space.persistence.SpaceRepository;

/** Scheduling, reading, editing and cancelling club events over HTTP (E11-S01). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class EventIntegrationTests {

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
    ApplicationEvents events;

    private final UUID founder = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();
    private final Instant start = Instant.now().plus(Duration.ofDays(3)).truncatedTo(ChronoUnit.SECONDS);
    private UUID clubId;
    private UUID general;       // PUBLIC, created with the club (E05-S01)
    private UUID boardRoom;     // CLUB_MEMBERS: members only

    /** An OPEN club with a member; the founder (owner) holds EVENT_MANAGE, the member does not. */
    @BeforeEach
    void aClubWithTwoSpaces() throws Exception {
        clubId = clubs.requestCreation(new Actor(founder, false),
            new CreateClubCommand("Chess " + TestRequests.suffix(), null, null, "Games", List.of())).id();
        clubs.approve(new Actor(UUID.randomUUID(), true), clubId);
        await().atMost(Duration.ofSeconds(10)).until(() ->
            authorization.isOwner(clubId, founder) && !spaces.findByClubIdOrderByPositionAsc(clubId).isEmpty());
        general = spaces.findByClubIdOrderByPositionAsc(clubId).getFirst().id();
        boardRoom = spaces.save(Space.create(clubId, "Board room", null, 1, AccessRule.clubMembers(), AccessRule.clubMembers(),
            Instant.now())).id();
        tx.executeWithoutResult(s -> clubRepository.findById(clubId).orElseThrow().changeJoinPolicy(JoinPolicy.OPEN, Instant.now()));
        send(post("/api/clubs/{id}/join", clubId), member, "{}").andExpect(status().isCreated());
    }

    private ResultActions send(MockHttpServletRequestBuilder request, UUID as, String json) throws Exception {
        return mvc.perform(request.with(user(as, false)).with(xsrf(mvc)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String eventJson(UUID spaceId, Instant startsAt, Instant endsAt) {
        return """
               {"spaceId":"%s","title":"Blitz night","description":"Bring a clock","location":"Cluster 2",
                "startsAt":"%s","endsAt":"%s"}""".formatted(spaceId, startsAt, endsAt);
    }

    private String schedule(UUID spaceId) throws Exception {
        String body = send(post("/api/clubs/{id}/events", clubId), founder, eventJson(spaceId, start, start.plus(Duration.ofHours(3))))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void anEventManagerSchedulesAndEveryReaderOfTheSpaceSeesIt() throws Exception {
        String id = schedule(general);

        assertThat(events.stream(EventScheduled.class).filter(e -> e.eventId().toString().equals(id))).hasSize(1);
        mvc.perform(get("/api/events/{id}", id).with(user(founder, false)))
            .andExpect(jsonPath("$.title").value("Blitz night"))
            .andExpect(jsonPath("$.status").value("SCHEDULED"))
            .andExpect(jsonPath("$.era").value(1))
            .andExpect(jsonPath("$.startsAt").value(start.toString()))
            .andExpect(jsonPath("$.canManage").value(true));
        mvc.perform(get("/api/events/{id}", id).with(user(stranger, false)))           // a PUBLIC space
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.clubName").value(startsWith("Chess")))
            .andExpect(jsonPath("$.canManage").value(false));
    }

    @Test
    void anEventInAMembersOnlySpaceIsMembersOnly() throws Exception {                     // R-06
        String id = schedule(boardRoom);

        mvc.perform(get("/api/events/{id}", id).with(user(member, false))).andExpect(status().isOk());
        mvc.perform(get("/api/events/{id}", id).with(user(stranger, false)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("SPACE_READ_DENIED"));
    }

    @Test
    void schedulingFollowsTheRules() throws Exception {
        send(post("/api/clubs/{id}/events", clubId), member, eventJson(general, start, start.plusSeconds(3600)))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        send(post("/api/clubs/{id}/events", clubId), founder, eventJson(general, start, start))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_EVENT_TIMES"));

        UUID otherClub = clubs.requestCreation(new Actor(founder, false),
            new CreateClubCommand("Go " + TestRequests.suffix(), null, null, "Games", List.of())).id();
        clubs.approve(new Actor(UUID.randomUUID(), true), otherClub);
        await().atMost(Duration.ofSeconds(10)).until(() -> !spaces.findByClubIdOrderByPositionAsc(otherClub).isEmpty());
        UUID foreignSpace = spaces.findByClubIdOrderByPositionAsc(otherClub).getFirst().id();
        send(post("/api/clubs/{id}/events", clubId), founder, eventJson(foreignSpace, start, start.plusSeconds(3600)))
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("SPACE_NOT_IN_CLUB"));   // BR-21
    }

    @Test
    void editingAndCancellingAreAnnouncedAndACancelledEventStaysVisible() throws Exception {
        String id = schedule(general);
        Instant later = start.plus(Duration.ofDays(1));

        send(patch("/api/events/{id}", id), founder, """
             {"title":"Blitz night (moved)","location":"Cluster 3","startsAt":"%s","endsAt":"%s"}"""
             .formatted(later, later.plus(Duration.ofHours(2))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Blitz night (moved)"))
            .andExpect(jsonPath("$.description").value(nullValue()))
            .andExpect(jsonPath("$.startsAt").value(later.toString()));
        send(post("/api/events/{id}/cancel", id), member, "{}").andExpect(status().isForbidden());
        send(post("/api/events/{id}/cancel", id), founder, "{\"reason\":\"Room taken\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.canManage").value(false));
        send(post("/api/events/{id}/cancel", id), founder, "{}")
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("EVENT_CANCELLED"));

        assertThat(events.stream(EventUpdated.class).filter(e -> e.eventId().toString().equals(id))).hasSize(1);
        assertThat(events.stream(EventCancelled.class).filter(e -> e.eventId().toString().equals(id)))
            .singleElement().satisfies(e -> assertThat(e.reason()).isEqualTo("Room taken"));
        mvc.perform(get("/api/events/{id}", id).with(user(stranger, false)))
            .andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.cancelReason").value("Room taken"));
    }

    @Test
    void anArchivedClubSchedulesNothing() throws Exception {
        clubs.archive(new Actor(UUID.randomUUID(), true), clubId, "Done");
        send(post("/api/clubs/{id}/events", clubId), founder, eventJson(general, start, start.plusSeconds(3600)))
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("CLUB_NOT_ACTIVE"));
        mvc.perform(get("/api/events/{id}", UUID.randomUUID()).with(user(founder, false)))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
    }
}
