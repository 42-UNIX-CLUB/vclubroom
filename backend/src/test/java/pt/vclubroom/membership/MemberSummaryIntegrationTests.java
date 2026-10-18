package pt.vclubroom.membership;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
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

/** GET /api/clubs/{clubId}/member-summary: the member count and owners shown on the club page (E04-S03). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MemberSummaryIntegrationTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    ClubService clubs;

    @Autowired
    ClubAuthorization authorization;

    @Autowired
    UserRepository users;

    private final UUID visitor = UUID.randomUUID();

    @Test
    void anyLoggedInUserSeesTheMemberCountAndTheOwners() throws Exception {
        String login = "founder-" + TestRequests.suffix();
        User founder = users.save(User.createLocal(login, "Ada Founder", login + "@example.test", "{noop}x",
            PlatformRole.USER, Instant.now()));
        UUID clubId = clubs.requestCreation(new Actor(founder.id(), false),
            new CreateClubCommand("Go Club " + TestRequests.suffix(), null, null, "Games", List.of())).id();
        clubs.approve(new Actor(UUID.randomUUID(), true), clubId);
        await().atMost(Duration.ofSeconds(10)).until(() -> authorization.isOwner(clubId, founder.id()));

        mvc.perform(get("/api/clubs/{clubId}/member-summary", clubId).with(TestRequests.user(visitor, false)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.memberCount").value(1))
            .andExpect(jsonPath("$.owners[0].displayName").value("Ada Founder"))
            .andExpect(jsonPath("$.owners[0].login").value(login));
    }

    @Test
    void aClubWithoutMembersHasNoOwners() throws Exception {
        mvc.perform(get("/api/clubs/{clubId}/member-summary", UUID.randomUUID()).with(TestRequests.user(visitor, false)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.memberCount").value(0))
            .andExpect(jsonPath("$.owners", empty()));
    }

    @Test
    void visitorsMustLogIn() throws Exception {
        mvc.perform(get("/api/clubs/{clubId}/member-summary", UUID.randomUUID()))
            .andExpect(status().isUnauthorized());
    }
}
