package pt.vclubroom.discovery;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pt.vclubroom.TestRequests;
import pt.vclubroom.TestcontainersConfiguration;
import pt.vclubroom.club.application.ClubDetails;
import pt.vclubroom.club.application.ClubService;
import pt.vclubroom.club.application.CreateClubCommand;
import pt.vclubroom.discovery.domain.ClubCard;
import pt.vclubroom.discovery.persistence.ClubCardRepository;
import pt.vclubroom.shared.security.Actor;

/**
 * The public directory, end to end and anonymous: approve clubs in the club module, wait for the projector, search.
 * The database is shared with every other test class, so each test searches for its own random suffix.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DirectoryIntegrationTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    ClubService clubs;

    @Autowired
    ClubCardRepository cards;

    private final Actor admin = new Actor(UUID.randomUUID(), true);
    private final String suffix = TestRequests.suffix();
    private ClubDetails robotics;
    private ClubDetails chess;
    private ClubDetails secret;

    @BeforeEach
    void twoApprovedClubsAndAPendingOne() {
        robotics = approved("Robotics " + suffix, "Build robots", "Tech", List.of("robots"));
        chess = approved("Chess " + suffix, "Blitz on Fridays", "Games", List.of("chess"));
        secret = clubs.requestCreation(new Actor(UUID.randomUUID(), false),
            new CreateClubCommand("Secret " + suffix, null, null, "Games", List.of()));
        for (ClubDetails club : List.of(robotics, chess)) {          // the projector runs after the commit, asynchronously
            await().atMost(Duration.ofSeconds(10)).until(() ->
                cards.findById(club.id()).map(ClubCard::memberCount).orElse(0) == 1 && cards.findById(club.id()).get().category() != null);
        }
    }

    private ClubDetails approved(String name, String tagline, String category, List<String> tags) {
        ClubDetails club = clubs.requestCreation(new Actor(UUID.randomUUID(), false),
            new CreateClubCommand(name, tagline, "About " + name, category, tags));
        clubs.approve(admin, club.id());
        return club;
    }

    @Test
    void theDirectoryShowsApprovedClubsAndFiltersThem() throws Exception {
        mvc.perform(get("/api/public/clubs").param("q", suffix).param("sort", "name"))        // no login, no CSRF
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(2))                                     // never "Secret" (BR-03)
            .andExpect(jsonPath("$.items[*].name", contains(chess.name(), robotics.name())))
            .andExpect(jsonPath("$.items[0].memberCount").value(1))
            .andExpect(jsonPath("$.items[0].avatarUrl").value(nullValue()));

        mvc.perform(get("/api/public/clubs").param("q", "robots " + suffix))                  // full text: tagline and tags
            .andExpect(jsonPath("$.items[*].name", contains(robotics.name())));
        mvc.perform(get("/api/public/clubs").param("q", suffix).param("category", "Games"))
            .andExpect(jsonPath("$.items[*].name", contains(chess.name())));
        mvc.perform(get("/api/public/clubs").param("q", suffix).param("tag", "robots"))
            .andExpect(jsonPath("$.items[*].name", contains(robotics.name())));
    }

    @Test
    void archivedClubsOnlyOnRequest() throws Exception {
        mvc.perform(get("/api/public/clubs").param("q", suffix).param("status", "ARCHIVED"))
            .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/public/clubs").param("q", suffix).param("status", "ALL"))
            .andExpect(jsonPath("$.items[*].name", containsInAnyOrder(chess.name(), robotics.name())));
        mvc.perform(get("/api/public/clubs").param("status", "DELETED"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void approvedClubsHaveAPublicProfilePendingOnesDoNot() throws Exception {
        mvc.perform(get("/api/public/clubs/{slug}", robotics.slug()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.description").value("About " + robotics.name()))
            .andExpect(jsonPath("$.tags", contains("robots")))
            .andExpect(jsonPath("$.memberCount").value(1))
            .andExpect(jsonPath("$.foundedAt").value(notNullValue()));
        mvc.perform(get("/api/public/clubs/{slug}", secret.slug()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("CLUB_NOT_FOUND"));
    }

    @Test
    void theLandingPageHasStatsListsAndCategories() throws Exception {
        mvc.perform(get("/api/public/landing"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stats.activeClubs").value(greaterThanOrEqualTo(2)))
            .andExpect(jsonPath("$.stats.memberships").value(greaterThanOrEqualTo(2)))
            .andExpect(jsonPath("$.mostActive.length()").value(lessThanOrEqualTo(6)))
            .andExpect(jsonPath("$.newest[*].name", hasItem(chess.name())))                    // approved last
            .andExpect(jsonPath("$.categories[0].name").value("Tech"))                         // configured order
            .andExpect(jsonPath("$.categories[0].count").value(greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.categories.length()").value(9));
    }
}
