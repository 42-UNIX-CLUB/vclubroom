package pt.vclubroom.space;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
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
import pt.vclubroom.membership.api.ClubAuthorization;
import pt.vclubroom.shared.security.Actor;

/** Managing spaces and channels (E05-S03): rules (BR-12, R-23), names, order, archiving. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SpaceManagementIntegrationTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    ClubService clubs;

    @Autowired
    ClubRepository clubRepository;

    @Autowired
    ClubAuthorization authorization;

    @Autowired
    TransactionTemplate tx;

    private final UUID founder = UUID.randomUUID();
    private final UUID ada = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private UUID clubId;
    private String board;

    private ResultActions send(MockHttpServletRequestBuilder request, UUID as, String json) throws Exception {
        return mvc.perform(request.with(user(as, false)).with(xsrf(mvc)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String space(String name, String read, String write) {
        return "{\"name\":\"" + name + "\",\"readRule\":" + read + ",\"writeRule\":" + write + "}";
    }

    private String rule(String level, String... groupIds) {
        String ids = String.join(",", java.util.Arrays.stream(groupIds).map(id -> "\"" + id + "\"").toList());
        return "{\"level\":\"" + level + "\",\"groupIds\":[" + ids + "]}";
    }

    private String create(String json) throws Exception {
        return JsonPath.read(send(post("/api/clubs/{id}/spaces", clubId), founder, json)
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private ResultActions spacesOf(UUID as, boolean includeArchived) throws Exception {
        return mvc.perform(get("/api/clubs/{id}/spaces", clubId).param("includeArchived", String.valueOf(includeArchived))
            .with(user(as, false)));
    }

    /** An OPEN club: its founder (owner) and two members; ada is in the Board group. */
    @BeforeEach
    void aClubWithABoard() throws Exception {
        clubId = clubs.requestCreation(new Actor(founder, false),
            new CreateClubCommand("Robotics " + TestRequests.suffix(), null, null, "Tech", List.of())).id();
        clubs.approve(new Actor(UUID.randomUUID(), true), clubId);
        await().atMost(Duration.ofSeconds(10)).until(() -> authorization.isOwner(clubId, founder));
        tx.executeWithoutResult(s -> clubRepository.findById(clubId).orElseThrow().changeJoinPolicy(JoinPolicy.OPEN, Instant.now()));
        for (UUID who : List.of(ada, bob)) {
            send(post("/api/clubs/{id}/join", clubId), who, "{}").andExpect(status().isCreated());
        }
        board = JsonPath.read(send(post("/api/clubs/{id}/groups", clubId), founder, "{\"name\":\"Board\"}")
            .andReturn().getResponse().getContentAsString(), "$.id");
        send(put("/api/clubs/{id}/groups/{g}/members/{u}", clubId, board, ada), founder, "").andExpect(status().isNoContent());
    }

    @Test
    void managersCreateRestrictedSpacesWithChannels() throws Exception {
        String boardRoom = create(space("Board room", rule("GROUPS", board), rule("GROUPS", board)));
        send(post("/api/clubs/{id}/spaces", clubId), founder, space("Board Room!", rule("PUBLIC"), rule("CLUB_MEMBERS")))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SPACE_NAME_TAKEN"));
        send(post("/api/clubs/{id}/spaces", clubId), founder, space("Empty", rule("GROUPS"), rule("GROUPS")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EMPTY_GROUP_RULE"));
        send(post("/api/clubs/{id}/spaces", clubId), founder, space("Elsewhere", rule("GROUPS", UUID.randomUUID().toString()), rule("CLUB_MEMBERS")))
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("GROUP_NOT_USABLE"));      // BR-12
        send(post("/api/clubs/{id}/spaces", clubId), ada, space("Mine", rule("PUBLIC"), rule("PUBLIC")))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));

        send(post("/api/spaces/{id}/channels", boardRoom), founder, "{\"type\":\"FORUM\",\"name\":\"Minutes\"}")
            .andExpect(status().isCreated()).andExpect(jsonPath("$.slug").value("minutes"));
        send(post("/api/spaces/{id}/channels", boardRoom), founder, "{\"type\":\"CHAT\",\"name\":\"minutes\"}")
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CHANNEL_NAME_TAKEN"));

        spacesOf(ada, false)
            .andExpect(jsonPath("$[?(@.name == 'Board room')].channels[*].name", contains("Minutes")))
            .andExpect(jsonPath("$[?(@.name == 'Board room')].readGroupIds[0]", contains(board)));
        spacesOf(bob, false).andExpect(jsonPath("$[*].name", not(hasItem("Board room"))));

        create(space("Notices", rule("CLUB_MEMBERS"), rule("PUBLIC")));                                    // R-23
        spacesOf(bob, false).andExpect(jsonPath("$[?(@.name == 'Notices')].writeLevel", contains("CLUB_MEMBERS")));
    }

    @Test
    void spacesAndChannelsAreRenamedMovedAndArchived() throws Exception {
        String lounge = create(space("Lounge two", rule("CLUB_MEMBERS"), rule("CLUB_MEMBERS")));
        String channel = JsonPath.read(send(post("/api/spaces/{id}/channels", lounge), founder, "{\"type\":\"FORUM\",\"name\":\"Ideas\"}")
            .andReturn().getResponse().getContentAsString(), "$.id");
        send(post("/api/spaces/{id}/channels", lounge), founder, "{\"type\":\"CHAT\",\"name\":\"Chat\"}").andExpect(status().isCreated());

        send(patch("/api/spaces/{id}", lounge), founder, "{\"name\":\"Hangout\",\"position\":0}").andExpect(status().isNoContent());
        spacesOf(ada, false)
            .andExpect(jsonPath("$[0].name").value("Hangout"))
            .andExpect(jsonPath("$[0].slug").value("lounge-two"))                                          // links keep working
            .andExpect(jsonPath("$[1].name").value("General"));

        send(patch("/api/channels/{id}", channel), founder, "{\"name\":\"Proposals\",\"position\":1}").andExpect(status().isNoContent());
        spacesOf(ada, false).andExpect(jsonPath("$[0].channels[*].name", contains("Chat", "Proposals")));
        send(post("/api/channels/{id}/archive", channel), founder, "").andExpect(status().isNoContent());
        spacesOf(ada, false).andExpect(jsonPath("$[0].channels[*].name", contains("Chat")));
        mvc.perform(get("/api/channels/{id}", channel).with(user(ada, false)))
            .andExpect(jsonPath("$.archived").value(true)).andExpect(jsonPath("$.canWrite").value(false));

        send(post("/api/spaces/{id}/archive", lounge), founder, "").andExpect(status().isNoContent());
        spacesOf(ada, false).andExpect(jsonPath("$[*].name", not(hasItem("Hangout"))));
        spacesOf(ada, true)
            .andExpect(jsonPath("$[?(@.name == 'Hangout')].archived", contains(true)))
            .andExpect(jsonPath("$[?(@.name == 'Hangout')].canWrite", contains(false)));
    }
}
