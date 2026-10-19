package pt.vclubroom.calendar;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pt.vclubroom.TestRequests.user;
import static pt.vclubroom.TestRequests.xsrf;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;
import pt.vclubroom.TestRequests;
import pt.vclubroom.TestcontainersConfiguration;
import pt.vclubroom.calendar.application.EventCommand;
import pt.vclubroom.calendar.application.EventService;
import pt.vclubroom.club.api.JoinPolicy;
import pt.vclubroom.club.application.ClubService;
import pt.vclubroom.club.application.CreateClubCommand;
import pt.vclubroom.club.persistence.ClubRepository;
import pt.vclubroom.membership.api.ClubAuthorization;
import pt.vclubroom.shared.security.Actor;
import pt.vclubroom.space.domain.AccessRule;
import pt.vclubroom.space.domain.Space;
import pt.vclubroom.space.persistence.SpaceRepository;

/** The campus and club calendars (E11-S02): only events of readable spaces, in the asked range. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CalendarIntegrationTests {

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
    EventService events;

    @Autowired
    TransactionTemplate tx;

    private final UUID member = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();
    /** A week of its own, far ahead, so the events of other test classes stay out of the picture. */
    private final Instant monday = Instant.now().plus(Duration.ofDays(400)).truncatedTo(ChronoUnit.DAYS);
    private UUID chess;
    private UUID go;

    private UUID approvedClub(String name, UUID founder) {
        UUID clubId = clubs.requestCreation(new Actor(founder, false),
            new CreateClubCommand(name + " " + TestRequests.suffix(), null, null, "Games", List.of())).id();
        clubs.approve(new Actor(UUID.randomUUID(), true), clubId);
        await().atMost(Duration.ofSeconds(10)).until(() ->
            authorization.isOwner(clubId, founder) && !spaces.findByClubIdOrderByPositionAsc(clubId).isEmpty());
        return clubId;
    }

    private UUID event(UUID clubId, UUID founder, UUID spaceId, String title, int day) {
        Instant start = monday.plus(Duration.ofDays(day)).plus(Duration.ofHours(18));
        return events.schedule(new Actor(founder, false), clubId,
            new EventCommand(spaceId, title, null, null, null, start, start.plus(Duration.ofHours(2)), null)).id();
    }

    /**
     * Chess: a public event on Tuesday, a members-only one on Wednesday, a cancelled one on Thursday, and one
     * outside the week. Go: a public event on Tuesday. The member belongs to Chess only.
     */
    @BeforeEach
    void twoClubsWithEvents() throws Exception {
        UUID chessFounder = UUID.randomUUID();
        UUID goFounder = UUID.randomUUID();
        chess = approvedClub("Chess", chessFounder);
        go = approvedClub("Go", goFounder);
        UUID general = spaces.findByClubIdOrderByPositionAsc(chess).getFirst().id();
        UUID board = spaces.save(Space.create(chess, "Board room", null, 1, AccessRule.clubMembers(), AccessRule.clubMembers(),
            Instant.now())).id();
        event(chess, chessFounder, general, "Blitz night", 1);
        event(chess, chessFounder, board, "Board meeting", 2);
        UUID simul = event(chess, chessFounder, general, "Simul", 3);
        events.cancel(new Actor(chessFounder, false), simul, "Too few players");
        event(chess, chessFounder, general, "Next month", 30);
        event(go, goFounder, spaces.findByClubIdOrderByPositionAsc(go).getFirst().id(), "Go night", 1);

        tx.executeWithoutResult(s -> clubRepository.findById(chess).orElseThrow().changeJoinPolicy(JoinPolicy.OPEN, Instant.now()));
        mvc.perform(post("/api/clubs/{id}/join", chess).with(user(member, false)).with(xsrf(mvc))).andExpect(status().isCreated());
    }

    private ResultActions week(String path, UUID as, Object... vars) throws Exception {
        return mvc.perform(get(path, vars).with(user(as, false))
            .param("from", monday.toString()).param("to", monday.plus(Duration.ofDays(7)).toString()));
    }

    private static String titlesOf(UUID clubId) {
        return "$[?(@.clubId == '" + clubId + "')].title";
    }

    @Test
    void theCampusCalendarShowsWhatEachViewerCanRead() throws Exception {
        week("/api/calendar", stranger)
            .andExpect(status().isOk())
            .andExpect(jsonPath(titlesOf(chess), contains("Blitz night", "Simul")))      // not the members-only meeting
            .andExpect(jsonPath(titlesOf(go), contains("Go night")))
            .andExpect(jsonPath("$[?(@.title == 'Simul')].status", contains("CANCELLED")));
        week("/api/calendar", member)
            .andExpect(jsonPath(titlesOf(chess), contains("Blitz night", "Board meeting", "Simul")));
    }

    @Test
    void myClubsOnly() throws Exception {
        mvc.perform(get("/api/calendar").with(user(member, false)).param("scope", "mine")
                .param("from", monday.toString()).param("to", monday.plus(Duration.ofDays(7)).toString()))
            .andExpect(jsonPath(titlesOf(chess), contains("Blitz night", "Board meeting", "Simul")))
            .andExpect(jsonPath(titlesOf(go), empty()));
        mvc.perform(get("/api/calendar").with(user(stranger, false)).param("scope", "mine")
                .param("from", monday.toString()).param("to", monday.plus(Duration.ofDays(7)).toString()))
            .andExpect(jsonPath("$", empty()));
    }

    @Test
    void aClubCalendarIsThatClubOnly() throws Exception {
        week("/api/clubs/{id}/events", stranger, chess)
            .andExpect(jsonPath("$[*].title", contains("Blitz night", "Simul")))
            .andExpect(jsonPath("$[0].clubSlug").value(startsWith("chess")));
        week("/api/clubs/{id}/events", member, chess)
            .andExpect(jsonPath("$[*].title", contains("Blitz night", "Board meeting", "Simul")));
    }

    @Test
    void rangesAreCheckedAndOffsetsUnderstood() throws Exception {
        String fromWithOffset = monday.atOffset(ZoneOffset.ofHours(1)).toString();       // what FullCalendar sends
        mvc.perform(get("/api/clubs/{id}/events", chess).with(user(member, false))
                .param("from", fromWithOffset).param("to", monday.plus(Duration.ofDays(2)).atOffset(ZoneOffset.ofHours(1)).toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].title", contains("Blitz night")));
        mvc.perform(get("/api/calendar").with(user(member, false))
                .param("from", monday.toString()).param("to", monday.plus(Duration.ofDays(63)).toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_RANGE"));
        mvc.perform(get("/api/calendar").with(user(member, false))
                .param("from", monday.toString()).param("to", monday.toString()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_RANGE"));
    }
}
