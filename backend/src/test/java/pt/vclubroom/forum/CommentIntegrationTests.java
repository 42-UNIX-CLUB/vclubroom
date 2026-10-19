package pt.vclubroom.forum;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pt.vclubroom.TestRequests.user;
import static pt.vclubroom.TestRequests.xsrf;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
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
import pt.vclubroom.TestRequests;
import pt.vclubroom.TestcontainersConfiguration;
import pt.vclubroom.club.application.ClubService;
import pt.vclubroom.club.application.CreateClubCommand;
import pt.vclubroom.membership.api.ClubAuthorization;
import pt.vclubroom.shared.security.Actor;
import pt.vclubroom.space.api.SpaceAccessPolicy;

/** Comments and replies over HTTP under a post in a real club's "Posts" channel (E09-S02). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CommentIntegrationTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    ClubService clubs;

    @Autowired
    ClubAuthorization authorization;

    @Autowired
    SpaceAccessPolicy spaceAccess;

    @Autowired
    JdbcTemplate jdbc;

    private final UUID founder = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();
    private UUID clubId;
    private UUID channelId;
    private String postId;

    @BeforeEach
    void aPostInAClub() throws Exception {
        clubId = clubs.requestCreation(new Actor(founder, false),
            new CreateClubCommand("Debate " + TestRequests.suffix(), null, null, "Culture", List.of())).id();
        clubs.approve(new Actor(UUID.randomUUID(), true), clubId);
        await().atMost(Duration.ofSeconds(10)).until(() ->
            authorization.isOwner(clubId, founder) && !spaceAccess.readableForumChannels(clubId, new Actor(founder, false)).isEmpty());
        channelId = spaceAccess.readableForumChannels(clubId, new Actor(founder, false)).iterator().next();
        String body = mvc.perform(post("/api/channels/{id}/posts", channelId).with(user(founder, false)).with(xsrf(mvc))
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Motion: pineapple belongs on pizza\"}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        postId = JsonPath.read(body, "$.id");
    }

    private ResultActions comment(UUID author, String json) throws Exception {
        return mvc.perform(post("/api/posts/{id}/comments", postId).with(user(author, false)).with(xsrf(mvc))
            .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String commentId(UUID author, String json) throws Exception {
        return JsonPath.read(comment(author, json).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    @Test
    void aWriterCommentsAndRepliesAndEveryReaderSeesTheThreadOldestFirst() throws Exception {
        String first = commentId(founder, "{\"body\":\"For: it is sweet\"}");
        comment(founder, "{\"body\":\"Against: it is fruit\"}").andExpect(status().isCreated());
        comment(founder, "{\"body\":\"Sweet is the point\",\"parentId\":\"" + first + "\"}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.parentId").value(first));

        mvc.perform(get("/api/posts/{id}/comments", postId).with(user(stranger, false)))           // a PUBLIC space
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].body", contains("For: it is sweet", "Against: it is fruit", "Sweet is the point")))
            .andExpect(jsonPath("$.items[0].parentId").value(nullValue()))
            .andExpect(jsonPath("$.items[2].parentId").value(first))
            .andExpect(jsonPath("$.nextCursor").value(nullValue()));
        mvc.perform(get("/api/channels/{id}/posts", channelId).with(user(stranger, false)))
            .andExpect(jsonPath("$.items[0].commentCount").value(3));
    }

    @Test
    void repliesGoOneLevelDeep() throws Exception {
        String first = commentId(founder, "{\"body\":\"Top\"}");
        String reply = commentId(founder, "{\"body\":\"Reply\",\"parentId\":\"" + first + "\"}");

        comment(founder, "{\"body\":\"Deeper\",\"parentId\":\"" + reply + "\"}")
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("INVALID_PARENT"));
        comment(founder, "{\"body\":\"To nothing\",\"parentId\":\"" + UUID.randomUUID() + "\"}")
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("INVALID_PARENT"));
    }

    @Test
    void readersCannotCommentAndCommentsNeedText() throws Exception {
        comment(stranger, "{\"body\":\"Hello\"}")
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("CHANNEL_WRITE_DENIED"));
        comment(founder, "{\"body\":\"   \"}").andExpect(status().isBadRequest());
        mvc.perform(post("/api/posts/{id}/comments", UUID.randomUUID()).with(user(founder, false)).with(xsrf(mvc))
                .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Hi\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
    }

    @Test
    void postsOfAnEarlierEraAreReadOnly() throws Exception {
        jdbc.update("update club.clubs set current_era = current_era + 1 where id = ?", clubId);   // as after a revival

        comment(founder, "{\"body\":\"Too late\"}")
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("HISTORY_READ_ONLY"));
        mvc.perform(get("/api/channels/{id}/posts", channelId).with(user(founder, false)))
            .andExpect(jsonPath("$.items[0].readOnly").value(true));
    }

    @Test
    void longThreadsComePageByPage() throws Exception {
        for (int i = 1; i <= 3; i++) {
            comment(founder, "{\"body\":\"Point " + i + "\"}").andExpect(status().isCreated());
        }
        String first = mvc.perform(get("/api/posts/{id}/comments", postId).param("limit", "2").with(user(founder, false)))
            .andExpect(jsonPath("$.items[*].body", contains("Point 1", "Point 2")))
            .andExpect(jsonPath("$.nextCursor").value(notNullValue()))
            .andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/posts/{id}/comments", postId).param("limit", "2")
                .param("cursor", JsonPath.<String>read(first, "$.nextCursor")).with(user(founder, false)))
            .andExpect(jsonPath("$.items[*].body", contains("Point 3")))
            .andExpect(jsonPath("$.nextCursor").value(nullValue()));
    }
}
