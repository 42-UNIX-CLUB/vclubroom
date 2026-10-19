package pt.vclubroom.space;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pt.vclubroom.TestRequests.user;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import pt.vclubroom.TestRequests;
import pt.vclubroom.TestcontainersConfiguration;
import pt.vclubroom.club.application.ClubService;
import pt.vclubroom.club.application.CreateClubCommand;
import pt.vclubroom.membership.api.ClubAuthorization;
import pt.vclubroom.shared.security.Actor;
import pt.vclubroom.space.api.ChannelType;
import pt.vclubroom.space.domain.AccessRule;
import pt.vclubroom.space.domain.Space;
import pt.vclubroom.space.persistence.SpaceRepository;

/** GET spaces and channels over HTTP (E05-S02): only what the viewer can read, with the right flags. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SpaceQueryIntegrationTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    ClubService clubs;

    @Autowired
    ClubAuthorization authorization;

    @Autowired
    SpaceRepository spaces;

    @Autowired
    TransactionTemplate tx;

    private final UUID founder = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();
    private UUID clubId;
    private Space lounge;

    /** An approved club (General space from E05-S01) plus a members-only space, created directly (no API until E05-S03). */
    @BeforeEach
    void aClubWithAPublicAndAPrivateSpace() {
        clubId = clubs.requestCreation(new Actor(founder, false),
            new CreateClubCommand("Chess " + TestRequests.suffix(), null, null, "Games", List.of())).id();
        clubs.approve(new Actor(UUID.randomUUID(), true), clubId);
        await().atMost(Duration.ofSeconds(10)).until(() -> authorization.isOwner(clubId, founder) && spaces.existsByClubId(clubId));
        lounge = Space.create(clubId, "Members lounge", null, 1, AccessRule.clubMembers(), AccessRule.clubMembers(), Instant.now());
        lounge.addChannel(ChannelType.CHAT, "Chat", Instant.now());
        spaces.save(lounge);
    }

    @Test
    void membersSeeEverySpaceTheyCanReadWithTheirFlags() throws Exception {
        mvc.perform(get("/api/clubs/{id}/spaces", clubId).with(user(founder, false)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].name", contains("General", "Members lounge")))
            .andExpect(jsonPath("$[0].readLevel").value("PUBLIC"))
            .andExpect(jsonPath("$[0].channels[*].name", contains("Posts", "Lounge")))
            .andExpect(jsonPath("$[0].channels[0].type").value("FORUM"))
            .andExpect(jsonPath("$[0].channels[0].canWrite").value(true))
            .andExpect(jsonPath("$[0].channels[0].canModerate").value(true));            // the founder is Owner
    }

    @Test
    void othersSeeOnlyPublicSpacesAndCannotWrite() throws Exception {
        mvc.perform(get("/api/clubs/{id}/spaces", clubId).with(user(stranger, false)))
            .andExpect(jsonPath("$[*].name", contains("General")))
            .andExpect(jsonPath("$[0].canWrite").value(false));
        mvc.perform(get("/api/spaces/{id}", lounge.id()).with(user(stranger, false)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("SPACE_READ_DENIED"));
        mvc.perform(get("/api/channels/{id}", lounge.channels().getFirst().id()).with(user(stranger, false)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("CHANNEL_READ_DENIED"));
    }

    @Test
    void aChannelComesWithItsSpaceFlagsAndTheCurrentEra() throws Exception {
        String posts = com.jayway.jsonpath.JsonPath.read(
            mvc.perform(get("/api/clubs/{id}/spaces", clubId).with(user(stranger, false))).andReturn().getResponse().getContentAsString(),
            "$[0].channels[0].id");

        mvc.perform(get("/api/channels/{id}", posts).with(user(stranger, false)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.type").value("FORUM"))
            .andExpect(jsonPath("$.spaceSlug").value("general"))
            .andExpect(jsonPath("$.slug").value("posts"))
            .andExpect(jsonPath("$.canWrite").value(false))
            .andExpect(jsonPath("$.currentEra").value(1));
        mvc.perform(get("/api/channels/{id}", UUID.randomUUID()).with(user(stranger, false)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("CHANNEL_NOT_FOUND"));
    }

    @Test
    void archivedSpacesOnlyOnRequestAndReadOnly() throws Exception {
        tx.executeWithoutResult(status -> spaces.findById(lounge.id()).orElseThrow().archive(Instant.now()));

        mvc.perform(get("/api/clubs/{id}/spaces", clubId).with(user(founder, false)))
            .andExpect(jsonPath("$[*].name", contains("General")));
        mvc.perform(get("/api/clubs/{id}/spaces", clubId).param("includeArchived", "true").with(user(founder, false)))
            .andExpect(jsonPath("$[1].archived").value(true))
            .andExpect(jsonPath("$[1].canWrite").value(false));
    }
}
