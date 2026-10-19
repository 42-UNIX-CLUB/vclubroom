package pt.vclubroom.forum;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import pt.vclubroom.TestRequests;
import pt.vclubroom.TestcontainersConfiguration;
import pt.vclubroom.club.api.JoinPolicy;
import pt.vclubroom.club.application.ClubService;
import pt.vclubroom.club.application.CreateClubCommand;
import pt.vclubroom.club.persistence.ClubRepository;
import pt.vclubroom.membership.api.ClubAuthorization;
import pt.vclubroom.shared.security.Actor;
import pt.vclubroom.space.api.ChannelType;
import pt.vclubroom.space.api.SpaceAccessPolicy;
import pt.vclubroom.space.domain.AccessRule;
import pt.vclubroom.space.domain.Space;
import pt.vclubroom.space.persistence.SpaceRepository;

/** The club feed (E09-S05): every forum channel the viewer can read, newest first, paged, per era. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FeedIntegrationTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    ClubService clubs;

    @Autowired
    ClubRepository clubRepository;

    @Autowired
    ClubAuthorization authorization;

    @Autowired
    SpaceAccessPolicy spaceAccess;

    @Autowired
    SpaceRepository spaces;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcTemplate jdbc;

    private final UUID founder = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();
    private UUID clubId;
    private String publicPost;
    private String minutesPost;

    private String publish(UUID channelId, String title) throws Exception {
        return JsonPath.read(mvc.perform(post("/api/channels/{id}/posts", channelId).with(user(founder, false)).with(xsrf(mvc))
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"" + title + "\",\"body\":\"…\"}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    /** A club with a public Posts channel and a members-only Minutes channel, one post in each (Minutes is newer). */
    @BeforeEach
    void aClubWithTwoForums() throws Exception {
        clubId = clubs.requestCreation(new Actor(founder, false),
            new CreateClubCommand("Chess " + TestRequests.suffix(), null, null, "Games", List.of())).id();
        clubs.approve(new Actor(UUID.randomUUID(), true), clubId);
        await().atMost(Duration.ofSeconds(10)).until(() -> authorization.isOwner(clubId, founder)
            && !spaceAccess.readableForumChannels(clubId, new Actor(founder, false)).isEmpty());
        UUID posts = spaceAccess.readableForumChannels(clubId, new Actor(founder, false)).iterator().next();
        Space board = Space.create(clubId, "Board room", null, 1, AccessRule.clubMembers(), AccessRule.clubMembers(), Instant.now());
        UUID minutes = board.addChannel(ChannelType.FORUM, "Minutes", Instant.now()).id();
        spaces.save(board);
        tx.executeWithoutResult(s -> clubRepository.findById(clubId).orElseThrow().changeJoinPolicy(JoinPolicy.OPEN, Instant.now()));
        mvc.perform(post("/api/clubs/{id}/join", clubId).with(user(member, false)).with(xsrf(mvc))).andExpect(status().isCreated());
        publicPost = publish(posts, "Friday blitz");
        minutesPost = publish(minutes, "Budget");
    }

    @Test
    void eachViewerSeesTheChannelsTheyCanReadNewestFirst() throws Exception {
        mvc.perform(get("/api/clubs/{id}/feed", clubId).with(user(member, false)))
            .andExpect(jsonPath("$.items[*].title", contains("Budget", "Friday blitz")))
            .andExpect(jsonPath("$.items[1].readOnly").value(false))
            .andExpect(jsonPath("$.nextCursor").value(nullValue()));
        mvc.perform(get("/api/clubs/{id}/feed", clubId).with(user(stranger, false)))
            .andExpect(jsonPath("$.items[*].title", contains("Friday blitz")))
            .andExpect(jsonPath("$.items[0].readOnly").value(true));                     // public to read, not to write

        String first = mvc.perform(get("/api/clubs/{id}/feed", clubId).param("limit", "1").with(user(member, false)))
            .andExpect(jsonPath("$.items[*].id", contains(minutesPost))).andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/clubs/{id}/feed", clubId).param("limit", "1").param("cursor", JsonPath.<String>read(first, "$.nextCursor"))
                .with(user(member, false)))
            .andExpect(jsonPath("$.items[*].id", contains(publicPost)));
    }

    @Test
    void anEraShowsOnlyItsPosts() throws Exception {
        jdbc.update("update forum.posts set era = 2 where id = ?::uuid", minutesPost);
        mvc.perform(get("/api/clubs/{id}/feed", clubId).param("era", "1").with(user(member, false)))
            .andExpect(jsonPath("$.items[*].title", contains("Friday blitz")));
        mvc.perform(get("/api/clubs/{id}/feed", clubId).param("era", "3").with(user(member, false)))
            .andExpect(jsonPath("$.items", empty()));
        mvc.perform(get("/api/clubs/{id}/feed", UUID.randomUUID()).with(user(member, false)))
            .andExpect(status().isNotFound());
    }
}
