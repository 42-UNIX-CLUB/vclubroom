package pt.vclubroom.club;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;

import jakarta.servlet.http.Cookie;
import pt.vclubroom.TestcontainersConfiguration;
import pt.vclubroom.club.events.ClubCreationRequested;
import pt.vclubroom.identity.infrastructure.security.ClubroomOAuth2User;

/**
 * E04-S01 acceptance criteria through the HTTP API. Same annotations as ClubroomApplicationTests, so Spring reuses
 * its cached context and containers; every test uses fresh user ids and club names because the database is shared.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class ClubCreationIntegrationTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    ApplicationEvents events;

    @Test
    void aRequestCreatesAPendingClubWithApprovalJoinPolicyAndAUniqueSlug() throws Exception {
        UUID founder = UUID.randomUUID();
        String name = "Chess Club " + suffix();

        String firstSlug = JsonPath.read(create(founder, name, "Games")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
            .andExpect(jsonPath("$.joinPolicy").value("APPROVAL"))
            .andExpect(jsonPath("$.founderId").value(founder.toString()))
            .andExpect(jsonPath("$.currentEra").value(0))
            .andReturn().getResponse().getContentAsString(), "$.slug");

        create(UUID.randomUUID(), name, "Games")        // same name, other student
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.slug").value(firstSlug + "-2"));
    }

    @Test
    void theRequestIsAnnouncedToTheOtherModules() throws Exception {
        UUID founder = UUID.randomUUID();
        create(founder, "Robotics " + suffix(), "Tech").andExpect(status().isCreated());

        assertThat(events.stream(ClubCreationRequested.class)).singleElement()
            .satisfies(e -> assertThat(e.founderId()).isEqualTo(founder));
    }

    @Test
    void aFourthPendingRequestIsRefused() throws Exception {
        UUID founder = UUID.randomUUID();
        for (int i = 1; i <= 3; i++) {
            create(founder, "Club " + i + " " + suffix(), "Social").andExpect(status().isCreated());
        }
        create(founder, "One Too Many " + suffix(), "Social")
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("TOO_MANY_PENDING_REQUESTS"));
    }

    @Test
    void aPendingClubIsVisibleOnlyToItsFounderAndAdmins() throws Exception {
        UUID founder = UUID.randomUUID();
        String body = create(founder, "Secret Garden " + suffix(), "Arts").andReturn().getResponse().getContentAsString();
        String slug = JsonPath.read(body, "$.slug");
        String id = JsonPath.read(body, "$.id");

        mvc.perform(get("/api/clubs/{slug}", slug).with(user(founder, false))).andExpect(status().isOk());
        mvc.perform(get("/api/clubs/{id}", id).with(user(founder, false))).andExpect(status().isOk());
        mvc.perform(get("/api/clubs/{slug}", slug).with(user(UUID.randomUUID(), true))).andExpect(status().isOk());
        mvc.perform(get("/api/clubs/{slug}", slug).with(user(UUID.randomUUID(), false)))
            .andExpect(status().isNotFound())                                  // hidden, not forbidden (BR-03)
            .andExpect(jsonPath("$.code").value("CLUB_NOT_FOUND"));
    }

    @Test
    void myRequestsListsOnlyMyOwnRequestsNewestFirst() throws Exception {
        UUID founder = UUID.randomUUID();
        create(founder, "First " + suffix(), "Music").andExpect(status().isCreated());
        create(founder, "Second " + suffix(), "Music").andExpect(status().isCreated());
        create(UUID.randomUUID(), "Someone Else " + suffix(), "Music").andExpect(status().isCreated());

        mvc.perform(get("/api/me/club-requests").with(user(founder, false)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].name").value(org.hamcrest.Matchers.startsWith("Second")));
    }

    @Test
    void invalidInputIsRefusedWithAStableCode() throws Exception {
        create(UUID.randomUUID(), "Cooking " + suffix(), "Cooking")            // not in app.clubs.categories
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_CATEGORY"));

        mvc.perform(post("/api/clubs").with(user(UUID.randomUUID(), false)).with(xsrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Ok Name\",\"category\":\"Games\",\"tags\":[\"Not Valid!\"]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.errors[0].field").value("tags[0]"));
    }

    @Test
    void loggedOutVisitorsCannotRequestAClub() throws Exception {
        mvc.perform(post("/api/clubs").with(xsrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Ghost Club\",\"category\":\"Games\"}"))
            .andExpect(status().isUnauthorized());
    }

    private ResultActions create(UUID founder, String name, String category) throws Exception {
        return mvc.perform(post("/api/clubs").with(user(founder, false)).with(xsrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"name": "%s", "tagline": "Meet every Friday", "description": "**Welcome!**",
                 "category": "%s", "tags": ["friday", "beginners"]}
                """.formatted(name, category)));
    }

    /**
     * CSRF the way the SPA does it: GET /api/auth/csrf, then echo the cookie in X-XSRF-TOKEN. (Spring Security's
     * csrf() post-processor would swap the cached context's cookie repository for a session-based one, and every
     * test running after it in the same context would stop receiving the XSRF-TOKEN cookie.)
     */
    private RequestPostProcessor xsrf() throws Exception {
        String token = mvc.perform(get("/api/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN").getValue();
        return request -> {
            request.setCookies(new Cookie("XSRF-TOKEN", token));
            request.addHeader("X-XSRF-TOKEN", token);
            return request;
        };
    }

    /** A logged-in principal as Login with 42 would create it (identity's ClubroomOAuth2User). */
    private static RequestPostProcessor user(UUID id, boolean admin) {
        return oauth2Login().oauth2User(new ClubroomOAuth2User(id, "user-" + id.toString().substring(0, 8), admin));
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 6);
    }
}