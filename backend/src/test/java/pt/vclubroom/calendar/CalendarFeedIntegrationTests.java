package pt.vclubroom.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pt.vclubroom.TestRequests.user;
import static pt.vclubroom.TestRequests.xsrf;

import biweekly.Biweekly;
import biweekly.ICalendar;
import biweekly.component.VEvent;
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
import org.springframework.test.web.servlet.MockMvc;
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

/** The personal calendar feed (E11-S05): a secret URL shown once, the readable events of my clubs, rotation and revocation. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CalendarFeedIntegrationTests {

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

    private final UUID founder = UUID.randomUUID();
    private User member;
    private UUID clubId;
    private String clubName;

    /** An OPEN club with a member; one event in General (PUBLIC), one in an owners-only space, one far ahead. */
    @BeforeEach
    void aClubWithEvents() throws Exception {
        String login = "feed-" + TestRequests.suffix();
        member = users.save(User.createLocal(login, "Fiona", null, "{noop}x", PlatformRole.USER, Instant.now()));
        clubName = "Astronomy " + TestRequests.suffix();
        clubId = clubs.requestCreation(new Actor(founder, false), new CreateClubCommand(clubName, null, null, "Science", List.of())).id();
        clubs.approve(new Actor(UUID.randomUUID(), true), clubId);
        await().atMost(Duration.ofSeconds(10)).until(() ->
            authorization.isOwner(clubId, founder) && !spaces.findByClubIdOrderByPositionAsc(clubId).isEmpty());
        UUID general = spaces.findByClubIdOrderByPositionAsc(clubId).getFirst().id();
        UUID board = spaces.save(Space.create(clubId, "Board", null, 1, AccessRule.groups(List.of()), AccessRule.groups(List.of()),
            Instant.now())).id();
        tx.executeWithoutResult(s -> clubRepository.findById(clubId).orElseThrow().changeJoinPolicy(JoinPolicy.OPEN, Instant.now()));
        mvc.perform(post("/api/clubs/{id}/join", clubId).with(user(member.id(), false)).with(xsrf(mvc))).andExpect(status().isCreated());

        schedule(general, "Star party", Duration.ofDays(3));
        schedule(board, "Budget meeting", Duration.ofDays(4));                         // the member can't read it
        schedule(general, "Next year's eclipse", Duration.ofDays(200));                 // past the feed's 180 days
    }

    private void schedule(UUID spaceId, String title, Duration ahead) throws Exception {
        Instant start = Instant.now().plus(ahead).truncatedTo(ChronoUnit.SECONDS);
        mvc.perform(post("/api/clubs/{id}/events", clubId).with(user(founder, false)).with(xsrf(mvc)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"spaceId\":\"%s\",\"title\":\"%s\",\"location\":\"Roof\",\"startsAt\":\"%s\",\"endsAt\":\"%s\"}"
                    .formatted(spaceId, title, start, start.plus(Duration.ofHours(2)))))
            .andExpect(status().isCreated());
    }

    private String createFeed() throws Exception {
        return JsonPath.read(mvc.perform(post("/api/me/calendar-feed").with(user(member.id(), false)).with(xsrf(mvc)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.url", startsWith("http://localhost:5173/ical/feeds/")))
            .andReturn().getResponse().getContentAsString(), "$.url");
    }

    private String path(String url) {
        return url.substring(url.indexOf("/ical/"));
    }

    @Test
    void theFeedHoldsTheReadableEventsOfMyClubs() throws Exception {
        mvc.perform(get("/api/me/calendar-feed").with(user(member.id(), false))).andExpect(jsonPath("$.createdAt").value(nullValue()));
        String url = createFeed();
        mvc.perform(get("/api/me/calendar-feed").with(user(member.id(), false)))
            .andExpect(jsonPath("$.url").value(nullValue()))                             // never shown again
            .andExpect(jsonPath("$.createdAt").isNotEmpty());

        String ics = mvc.perform(get(path(url)))                                         // no session: the secret is enough
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", startsWith("text/calendar")))
            .andReturn().getResponse().getContentAsString();
        ICalendar calendar = Biweekly.parse(ics).first();
        assertThat(calendar.getEvents()).extracting(e -> e.getSummary().getValue()).containsExactly("[" + clubName + "] Star party");
        VEvent party = calendar.getEvents().getFirst();
        assertThat(party.getLocation().getValue()).isEqualTo("Roof");
        assertThat(party.getUrl().getValue()).startsWith("http://localhost:5173/events/");
    }

    @Test
    void aNewLinkReplacesTheOldOneAndTurningItOffEndsBoth() throws Exception {
        String first = createFeed();
        String second = createFeed();
        mvc.perform(get(path(first))).andExpect(status().isNotFound());
        mvc.perform(get(path(second))).andExpect(status().isOk());

        mvc.perform(delete("/api/me/calendar-feed").with(user(member.id(), false)).with(xsrf(mvc))).andExpect(status().isNoContent());
        mvc.perform(get(path(second))).andExpect(status().isNotFound());
        mvc.perform(get("/ical/feeds/not-a-real-token.ics")).andExpect(status().isNotFound());
    }

    @Test
    void aDisabledAccountsFeedStops() throws Exception {
        String url = createFeed();
        tx.executeWithoutResult(s -> users.findById(member.id()).orElseThrow().disable(Instant.now()));
        mvc.perform(get(path(url))).andExpect(status().isNotFound());
    }
}
