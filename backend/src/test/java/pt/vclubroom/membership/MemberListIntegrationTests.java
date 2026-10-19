package pt.vclubroom.membership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pt.vclubroom.TestRequests.user;
import static pt.vclubroom.TestRequests.xsrf;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
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

/** The member list and member removal (E07-S04): members see each other; MEMBER_REMOVE never touches owners. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MemberListIntegrationTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    ClubService clubs;

    @Autowired
    ClubRepository clubRepository;

    @Autowired
    ClubAuthorization authorization;

    @Autowired
    UserRepository users;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcTemplate jdbc;

    private final UUID admin = UUID.randomUUID();
    private UUID founder;
    private UUID bob;
    private UUID carol;
    private UUID clubId;

    private UUID person(String name) {
        String login = name.toLowerCase().replace(' ', '.') + "-" + TestRequests.suffix();
        return users.save(User.createLocal(login, name, login + "@example.test", "{noop}x", PlatformRole.USER, Instant.now())).id();
    }

    private ResultActions members(UUID as, String query) throws Exception {
        return mvc.perform(get("/api/clubs/{id}/members" + query, clubId).with(user(as, as.equals(admin))));
    }

    private ResultActions remove(UUID as, UUID who) throws Exception {
        return mvc.perform(delete("/api/clubs/{id}/members/{userId}", clubId, who).with(user(as, as.equals(admin))).with(xsrf(mvc)));
    }

    /** An OPEN club: its founder (the owner), and bob and carol, who joined. */
    @BeforeEach
    void aClubWithMembers() throws Exception {
        founder = person("Ada Founder");
        bob = person("Bob Martins");
        carol = person("Carol Silva");
        clubId = clubs.requestCreation(new Actor(founder, false),
            new CreateClubCommand("Chess " + TestRequests.suffix(), null, null, "Games", List.of())).id();
        clubs.approve(new Actor(admin, true), clubId);
        await().atMost(Duration.ofSeconds(10)).until(() -> authorization.isOwner(clubId, founder));
        tx.executeWithoutResult(s -> clubRepository.findById(clubId).orElseThrow().changeJoinPolicy(JoinPolicy.OPEN, Instant.now()));
        for (UUID who : List.of(carol, bob)) {
            mvc.perform(post("/api/clubs/{id}/join", clubId).with(user(who, false)).with(xsrf(mvc))).andExpect(status().isCreated());
        }
    }

    @Test
    void membersSeeWhoIsInTheClubOwnersFirst() throws Exception {
        members(bob, "")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].displayName", contains("Ada Founder", "Bob Martins", "Carol Silva")))
            .andExpect(jsonPath("$.items[0].owner").value(true))
            .andExpect(jsonPath("$.items[0].roles[*].name", hasItem("Owner")))
            .andExpect(jsonPath("$.items[1].owner").value(false))
            .andExpect(jsonPath("$.totalElements").value(3));
        members(bob, "?query=SIL").andExpect(jsonPath("$.items[*].displayName", contains("Carol Silva")));
        members(bob, "?page=1&size=1").andExpect(jsonPath("$.items[*].displayName", contains("Bob Martins")))
            .andExpect(jsonPath("$.totalElements").value(3));

        members(UUID.randomUUID(), "").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("MEMBERS_ONLY"));
        members(admin, "").andExpect(status().isOk());                                       // R-08
    }

    @Test
    void ownersRemoveMembersButNobodyRemovesAnOwner() throws Exception {
        remove(bob, carol).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));

        remove(founder, carol).andExpect(status().isNoContent());
        assertThat(authorization.activeMemberIds(clubId)).doesNotContain(carol);
        assertThat(jdbc.queryForObject("select status from membership.memberships where club_id = ? and user_id = ?",
            String.class, clubId, carol)).isEqualTo("REMOVED");
        remove(founder, carol).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));

        remove(founder, founder).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CANNOT_REMOVE_OWNER"));
        remove(admin, founder).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CANNOT_REMOVE_OWNER"));
        members(founder, "").andExpect(jsonPath("$.items[*].displayName", contains("Ada Founder", "Bob Martins")));
    }
}
