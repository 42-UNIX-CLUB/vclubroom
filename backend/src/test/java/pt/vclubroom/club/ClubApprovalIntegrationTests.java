package pt.vclubroom.club;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pt.vclubroom.TestRequests.suffix;
import static pt.vclubroom.TestRequests.user;
import static pt.vclubroom.TestRequests.xsrf;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import pt.vclubroom.TestcontainersConfiguration;
import pt.vclubroom.club.application.ClubDetails;
import pt.vclubroom.club.application.ClubService;
import pt.vclubroom.club.application.CreateClubCommand;
import pt.vclubroom.shared.security.Actor;

/** E04-S02 acceptance criteria through the HTTP API (same annotations: the cached context is reused). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ClubApprovalIntegrationTests {

    private final UUID admin = UUID.randomUUID();

    @Autowired
    MockMvc mvc;

    @Autowired
    ClubService clubs;

    private ClubDetails pendingClub(UUID founder) {
        return clubs.requestCreation(new Actor(founder, false),
            new CreateClubCommand("Board Games " + suffix(), null, null, "Games", List.of()));
    }

    @Test
    void adminsSeeThePendingRequestsOldestFirst() throws Exception {
        ClubDetails first = pendingClub(UUID.randomUUID());
        ClubDetails second = pendingClub(UUID.randomUUID());

        String body = mvc.perform(get("/api/admin/clubs").param("status", "PENDING_APPROVAL").param("size", "100").with(user(admin, true)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(Matchers.greaterThanOrEqualTo(2)))
            .andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(body, "$.items[*].id");
        org.assertj.core.api.Assertions.assertThat(ids.indexOf(first.id().toString()))
            .isLessThan(ids.indexOf(second.id().toString()));
    }

    @Test
    void approvingMakesTheClubActiveWithEraOneAndPublic() throws Exception {
        ClubDetails pending = pendingClub(UUID.randomUUID());

        mvc.perform(post("/api/admin/clubs/{id}/approve", pending.id()).with(user(admin, true)).with(xsrf(mvc)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.currentEra").value(1))
            .andExpect(jsonPath("$.eras[0].startReason").value("FOUNDED"));

        mvc.perform(get("/api/clubs/{slug}", pending.slug()).with(user(UUID.randomUUID(), false)))
            .andExpect(status().isOk());                                         // no longer hidden (BR-03)
        mvc.perform(post("/api/admin/clubs/{id}/approve", pending.id()).with(user(admin, true)).with(xsrf(mvc)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("CLUB_NOT_PENDING"));
    }

    @Test
    void rejectingNeedsAReasonThatTheFounderThenSees() throws Exception {
        UUID founder = UUID.randomUUID();
        ClubDetails pending = pendingClub(founder);

        mvc.perform(post("/api/admin/clubs/{id}/reject", pending.id()).with(user(admin, true)).with(xsrf(mvc))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"  \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mvc.perform(post("/api/admin/clubs/{id}/reject", pending.id()).with(user(admin, true)).with(xsrf(mvc))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Please merge with the Games club\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REJECTED"));

        mvc.perform(get("/api/me/club-requests").with(user(founder, false)))
            .andExpect(jsonPath("$[0].status").value("REJECTED"))
            .andExpect(jsonPath("$[0].reviewNote").value("Please merge with the Games club"));
    }

    @Test
    void nonAdminsAreForbiddenEverywhereUnderApiAdmin() throws Exception {
        ClubDetails pending = pendingClub(UUID.randomUUID());
        UUID student = UUID.randomUUID();

        mvc.perform(get("/api/admin/clubs").with(user(student, false))).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/clubs/{id}/approve", pending.id()).with(user(student, false)).with(xsrf(mvc)))
            .andExpect(status().isForbidden());
    }

    @Test
    void anUnknownClubIsNotFound() throws Exception {
        mvc.perform(post("/api/admin/clubs/{id}/approve", UUID.randomUUID()).with(user(admin, true)).with(xsrf(mvc)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("CLUB_NOT_FOUND"));
    }
}
