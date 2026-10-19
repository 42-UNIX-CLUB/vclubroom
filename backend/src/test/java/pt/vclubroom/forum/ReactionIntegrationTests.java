package pt.vclubroom.forum;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.transaction.support.TransactionTemplate;
import pt.vclubroom.TestRequests;
import pt.vclubroom.TestcontainersConfiguration;
import pt.vclubroom.club.api.JoinPolicy;
import pt.vclubroom.club.application.ClubService;
import pt.vclubroom.club.application.CreateClubCommand;
import pt.vclubroom.club.persistence.ClubRepository;
import pt.vclubroom.membership.api.ClubAuthorization;
import pt.vclubroom.shared.security.Actor;
import pt.vclubroom.space.api.SpaceAccessPolicy;

/** Emoji reactions on posts and comments over HTTP (E09-S03). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ReactionIntegrationTests {

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
    TransactionTemplate tx;

    @Autowired
    JdbcTemplate jdbc;

    private final UUID founder = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();
    private UUID clubId;
    private UUID channelId;
    private String postId;
    private String commentId;

    /** An OPEN club with a member, a post and a comment. */
    @BeforeEach
    void aPostWithAComment() throws Exception {
        clubId = clubs.requestCreation(new Actor(founder, false),
            new CreateClubCommand("Karaoke " + TestRequests.suffix(), null, null, "Music", List.of())).id();
        clubs.approve(new Actor(UUID.randomUUID(), true), clubId);
        await().atMost(Duration.ofSeconds(10)).until(() ->
            authorization.isOwner(clubId, founder) && !spaceAccess.readableForumChannels(clubId, new Actor(founder, false)).isEmpty());
        channelId = spaceAccess.readableForumChannels(clubId, new Actor(founder, false)).iterator().next();
        tx.executeWithoutResult(s -> clubRepository.findById(clubId).orElseThrow().changeJoinPolicy(JoinPolicy.OPEN, Instant.now()));
        mvc.perform(post("/api/clubs/{id}/join", clubId).with(user(member, false)).with(xsrf(mvc))).andExpect(status().isCreated());
        postId = JsonPath.read(mvc.perform(post("/api/channels/{id}/posts", channelId).with(user(founder, false)).with(xsrf(mvc))
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Friday night: Bohemian Rhapsody\"}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        commentId = JsonPath.read(mvc.perform(post("/api/posts/{id}/comments", postId).with(user(member, false)).with(xsrf(mvc))
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"I call Freddie\"}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private ResultActions react(String target, String id, String code, UUID as, boolean add) throws Exception {
        var request = add ? put("/api/{target}/{id}/reactions/{code}", target, id, code)
                          : delete("/api/{target}/{id}/reactions/{code}", target, id, code);
        return mvc.perform(request.with(user(as, false)).with(xsrf(mvc)));
    }

    @Test
    void theFixedSetComesInOrder() throws Exception {
        mvc.perform(get("/api/reactions").with(user(stranger, false)))
            .andExpect(jsonPath("$[*].code", contains("thumbs_up", "heart", "laugh", "party", "wow", "sad")))
            .andExpect(jsonPath("$[3].emoji").value("🎉"));
    }

    @Test
    void addingAndRemovingAreIdempotentAndEveryoneSeesTheCounts() throws Exception {
        react("posts", postId, "party", member, true).andExpect(status().isOk());
        react("posts", postId, "party", member, true)                                   // again: still one (BR-18)
            .andExpect(jsonPath("$.counts.party").value(1))
            .andExpect(jsonPath("$.mine", contains("party")));
        react("posts", postId, "party", founder, true).andExpect(jsonPath("$.counts.party").value(2));
        react("posts", postId, "heart", founder, true).andExpect(jsonPath("$.mine.length()").value(2));

        mvc.perform(get("/api/channels/{id}/posts", channelId).with(user(member, false)))
            .andExpect(jsonPath("$.items[0].reactionCounts.party").value(2))
            .andExpect(jsonPath("$.items[0].reactionCounts.heart").value(1))
            .andExpect(jsonPath("$.items[0].myReactions", contains("party")));
        mvc.perform(get("/api/channels/{id}/posts", channelId).with(user(stranger, false)))   // readers see counts
            .andExpect(jsonPath("$.items[0].reactionCounts.party").value(2))
            .andExpect(jsonPath("$.items[0].myReactions", empty()));

        react("posts", postId, "party", member, false).andExpect(jsonPath("$.counts.party").value(1));
        react("posts", postId, "party", member, false)                                  // again: nothing to remove
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.counts.party").value(1))
            .andExpect(jsonPath("$.mine", empty()));
    }

    @Test
    void commentsHaveReactionsToo() throws Exception {
        react("comments", commentId, "laugh", founder, true).andExpect(jsonPath("$.counts.laugh").value(1));
        mvc.perform(get("/api/posts/{id}/comments", postId).with(user(founder, false)))
            .andExpect(jsonPath("$.items[0].reactionCounts.laugh").value(1))
            .andExpect(jsonPath("$.items[0].myReactions", contains("laugh")));
        react("comments", UUID.randomUUID().toString(), "laugh", founder, true)
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("COMMENT_NOT_FOUND"));
    }

    @Test
    void onlyTheFixedSetOnlyForWritersOnlyInTheCurrentEra() throws Exception {
        react("posts", postId, "poop", member, true)
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_REACTION"));
        react("posts", postId, "party", stranger, true)
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CHANNEL_WRITE_DENIED"));
        jdbc.update("update club.clubs set current_era = current_era + 1 where id = ?", clubId);
        react("comments", commentId, "party", member, true)
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("HISTORY_READ_ONLY"));
    }
}
