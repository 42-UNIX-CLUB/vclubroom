package pt.vclubroom.group;

import static org.assertj.core.api.Assertions.assertThat;
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

/** Club groups (E13-S03): managing them, who can be in them (BR-13), and what they open (space rules, R-22). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ClubGroupIntegrationTests {

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

    private UUID founder;
    private UUID ada;
    private UUID bob;
    private UUID clubId;

    private UUID person(String name) {
        String login = name.toLowerCase().replace(' ', '.') + "-" + TestRequests.suffix();
        return users.save(User.createLocal(login, name, login + "@example.test", "{noop}x", PlatformRole.USER, Instant.now())).id();
    }

    private ResultActions send(MockHttpServletRequestBuilder request, UUID as, String json) throws Exception {
        return mvc.perform(request.with(user(as, false)).with(xsrf(mvc)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String createGroup(String name) throws Exception {
        return JsonPath.read(send(post("/api/clubs/{id}/groups", clubId), founder, "{\"name\":\"" + name + "\"}")
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private ResultActions visibleSpaces(UUID as) throws Exception {
        return mvc.perform(get("/api/clubs/{id}/spaces", clubId).with(user(as, false)));
    }

    /** An OPEN club: its founder (the owner) and two members, ada and bob. */
    @BeforeEach
    void aClubWithMembers() throws Exception {
        founder = person("Dan Founder");
        ada = person("Ada Lopes");
        bob = person("Bob Martins");
        clubId = clubs.requestCreation(new Actor(founder, false),
            new CreateClubCommand("Robotics " + TestRequests.suffix(), null, null, "Tech", List.of())).id();
        clubs.approve(new Actor(UUID.randomUUID(), true), clubId);
        await().atMost(Duration.ofSeconds(10)).until(() -> authorization.isOwner(clubId, founder));
        tx.executeWithoutResult(s -> clubRepository.findById(clubId).orElseThrow().changeJoinPolicy(JoinPolicy.OPEN, Instant.now()));
        for (UUID who : List.of(ada, bob)) {
            send(post("/api/clubs/{id}/join", clubId), who, "{}").andExpect(status().isCreated());
        }
    }

    @Test
    void managersShapeGroupsOfMembersOnly() throws Exception {
        String board = createGroup("Board");
        send(post("/api/clubs/{id}/groups", clubId), founder, "{\"name\":\"board\"}")
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("GROUP_NAME_TAKEN"));
        send(post("/api/clubs/{id}/groups", clubId), ada, "{\"name\":\"Mine\"}")
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));

        send(put("/api/clubs/{id}/groups/{g}/members/{u}", clubId, board, ada), founder, "").andExpect(status().isNoContent());
        send(put("/api/clubs/{id}/groups/{g}/members/{u}", clubId, board, ada), founder, "").andExpect(status().isNoContent());
        send(put("/api/clubs/{id}/groups/{g}/members/{u}", clubId, board, UUID.randomUUID()), founder, "")
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("NOT_A_MEMBER"));     // BR-13

        mvc.perform(get("/api/clubs/{id}/groups", clubId).with(user(bob, false)))
            .andExpect(jsonPath("$[0].name").value("Board"))
            .andExpect(jsonPath("$[0].slug").value("board"))
            .andExpect(jsonPath("$[0].memberCount").value(1));
        mvc.perform(get("/api/clubs/{id}/groups/{g}/members", clubId, board).with(user(bob, false)))
            .andExpect(jsonPath("$[*].user.displayName", contains("Ada Lopes")));
        mvc.perform(get("/api/clubs/{id}/groups", clubId).with(user(UUID.randomUUID(), false)))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("MEMBERS_ONLY"));

        send(patch("/api/clubs/{id}/groups/{g}", clubId, board), founder, "{\"name\":\"The Board\",\"description\":\"Runs the club\"}")
            .andExpect(jsonPath("$.name").value("The Board")).andExpect(jsonPath("$.slug").value("board"));
        send(delete("/api/clubs/{id}/groups/{g}/members/{u}", clubId, board, ada), founder, "").andExpect(status().isNoContent());
        mvc.perform(get("/api/clubs/{id}/groups/{g}/members", clubId, board).with(user(bob, false))).andExpect(jsonPath("$", empty()));
    }

    @Test
    void aGroupOpensASpaceToItsMembersUntilTheyLeaveOrTheGroupGoes() throws Exception {
        String board = createGroup("Board");
        send(put("/api/clubs/{id}/groups/{g}/members/{u}", clubId, board, ada), founder, "").andExpect(status().isNoContent());
        AccessRule boardOnly = AccessRule.groups(List.of(UUID.fromString(board)));
        UUID boardRoom = spaces.save(Space.create(clubId, "Board room", null, 1, boardOnly, boardOnly, Instant.now())).id();

        visibleSpaces(ada).andExpect(jsonPath("$[*].name", hasItem("Board room")));
        visibleSpaces(bob).andExpect(jsonPath("$[*].name", not(hasItem("Board room"))));

        send(delete("/api/clubs/{id}/members/me", clubId), ada, "").andExpect(status().isNoContent());      // R-22
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
            mvc.perform(get("/api/clubs/{id}/groups/{g}/members", clubId, board).with(user(bob, false))).andExpect(jsonPath("$", empty())));
        send(post("/api/clubs/{id}/join", clubId), ada, "{}").andExpect(status().isCreated());
        visibleSpaces(ada).andExpect(jsonPath("$[*].name", not(hasItem("Board room"))));                    // back, not in the group

        send(put("/api/clubs/{id}/groups/{g}/members/{u}", clubId, board, bob), founder, "").andExpect(status().isNoContent());
        send(delete("/api/clubs/{id}/groups/{g}", clubId, board), founder, "").andExpect(status().isNoContent());
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(jdbc.queryForObject(
            "select cardinality(read_group_ids) + cardinality(write_group_ids) from space.spaces where id = ?", Integer.class, boardRoom)).isZero());
        visibleSpaces(bob).andExpect(jsonPath("$[*].name", not(hasItem("Board room"))));                    // fails closed
        visibleSpaces(founder).andExpect(jsonPath("$[*].name", hasItem("Board room")));                     // owners see every space
    }
}
