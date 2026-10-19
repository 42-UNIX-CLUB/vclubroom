package pt.vclubroom.membership;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pt.vclubroom.TestRequests.user;
import static pt.vclubroom.TestRequests.xsrf;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
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
import pt.vclubroom.TestRequests;
import pt.vclubroom.TestcontainersConfiguration;
import pt.vclubroom.club.application.ClubService;
import pt.vclubroom.club.application.CreateClubCommand;
import pt.vclubroom.identity.api.PlatformRole;
import pt.vclubroom.identity.domain.User;
import pt.vclubroom.identity.persistence.UserRepository;
import pt.vclubroom.membership.api.ClubAuthorization;
import pt.vclubroom.shared.security.Actor;

/** The join request queue over HTTP (E07-S03): list, approve, reject, cancel. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class JoinRequestIntegrationTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    ClubService clubs;

    @Autowired
    ClubAuthorization authorization;

    @Autowired
    UserRepository users;

    private final UUID founder = UUID.randomUUID();
    private UUID student;
    private UUID clubId;

    /** An approved club (APPROVAL, the default) and a real student who asked to join it. */
    @BeforeEach
    void aClubWithOneRequest() throws Exception {
        String login = "student-" + TestRequests.suffix();
        student = users.save(User.createLocal(login, "Sam Student", login + "@example.test", "{noop}x",
            PlatformRole.USER, Instant.now())).id();
        clubId = clubs.requestCreation(new Actor(founder, false),
            new CreateClubCommand("Debate " + TestRequests.suffix(), null, null, "Culture", List.of())).id();
        clubs.approve(new Actor(UUID.randomUUID(), true), clubId);
        await().atMost(Duration.ofSeconds(10)).until(() -> authorization.isOwner(clubId, founder));
        mvc.perform(post("/api/clubs/{id}/join", clubId).with(user(student, false)).with(xsrf(mvc))
                .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"I argue a lot\"}"))
            .andExpect(status().isAccepted());
    }

    private String pendingRequestId() throws Exception {
        String body = mvc.perform(get("/api/clubs/{id}/join-requests", clubId).with(user(founder, false)))
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$[0].id");
    }

    @Test
    void theOwnerSeesWhoAskedAndApproves() throws Exception {
        mvc.perform(get("/api/clubs/{id}/join-requests", clubId).with(user(founder, false)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].requester.displayName").value("Sam Student"))
            .andExpect(jsonPath("$[0].message").value("I argue a lot"));
        String requestId = pendingRequestId();

        mvc.perform(post("/api/clubs/{id}/join-requests/{rid}/approve", clubId, requestId).with(user(founder, false)).with(xsrf(mvc)))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/clubs/{id}/membership/me", clubId).with(user(student, false)))
            .andExpect(jsonPath("$.member").value(true));
        mvc.perform(post("/api/clubs/{id}/join-requests/{rid}/approve", clubId, requestId).with(user(founder, false)).with(xsrf(mvc)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("JOIN_REQUEST_NOT_PENDING"));
        mvc.perform(get("/api/clubs/{id}/join-requests", clubId).with(user(founder, false)))
            .andExpect(jsonPath("$", empty()));
    }

    @Test
    void theOwnerRejects() throws Exception {
        mvc.perform(post("/api/clubs/{id}/join-requests/{rid}/reject", clubId, pendingRequestId()).with(user(founder, false)).with(xsrf(mvc)))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/clubs/{id}/membership/me", clubId).with(user(student, false)))
            .andExpect(jsonPath("$.member").value(false))
            .andExpect(jsonPath("$.pendingRequest").value(nullValue()));
    }

    @Test
    void onlyApproversSeeOrDecide() throws Exception {
        String requestId = pendingRequestId();
        UUID stranger = UUID.randomUUID();

        mvc.perform(get("/api/clubs/{id}/join-requests", clubId).with(user(stranger, false)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        mvc.perform(post("/api/clubs/{id}/join-requests/{rid}/approve", clubId, requestId).with(user(stranger, false)).with(xsrf(mvc)))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/clubs/{id}/join-requests/{rid}/approve", clubId, UUID.randomUUID()).with(user(founder, false)).with(xsrf(mvc)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("JOIN_REQUEST_NOT_FOUND"));
    }

    @Test
    void theRequesterCancels() throws Exception {
        mvc.perform(delete("/api/clubs/{id}/join-requests/mine", clubId).with(user(student, false)).with(xsrf(mvc)))
            .andExpect(status().isNoContent());
        mvc.perform(delete("/api/clubs/{id}/join-requests/mine", clubId).with(user(student, false)).with(xsrf(mvc)))
            .andExpect(status().isNotFound());
        mvc.perform(get("/api/clubs/{id}/join-requests", clubId).with(user(founder, false)))
            .andExpect(jsonPath("$", empty()));
    }
}
