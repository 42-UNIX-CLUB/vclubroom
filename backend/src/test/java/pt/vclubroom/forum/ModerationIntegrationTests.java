package pt.vclubroom.forum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

/**
 * Editing, deleting and pinning over HTTP (E09-S04): authors change their own content, moderators (the founder, an
 * owner) remove anyone's and pin, deleted content stays as a tombstone (BR-16), and earlier eras are read-only.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ModerationIntegrationTests {

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
    private final UUID ada = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private UUID clubId;
    private UUID channelId;

    @BeforeEach
    void anOpenClubWithTwoMembers() throws Exception {
        clubId = clubs.requestCreation(new Actor(founder, false),
            new CreateClubCommand("Poetry " + TestRequests.suffix(), null, null, "Culture", List.of())).id();
        clubs.approve(new Actor(UUID.randomUUID(), true), clubId);
        await().atMost(Duration.ofSeconds(10)).until(() ->
            authorization.isOwner(clubId, founder) && !spaceAccess.readableForumChannels(clubId, new Actor(founder, false)).isEmpty());
        channelId = spaceAccess.readableForumChannels(clubId, new Actor(founder, false)).iterator().next();
        tx.executeWithoutResult(s -> clubRepository.findById(clubId).orElseThrow().changeJoinPolicy(JoinPolicy.OPEN, Instant.now()));
        for (UUID member : List.of(ada, bob)) {
            mvc.perform(post("/api/clubs/{id}/join", clubId).with(user(member, false)).with(xsrf(mvc))).andExpect(status().isCreated());
        }
    }

    private String json(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    private String postBy(UUID author, String body) throws Exception {
        return JsonPath.read(json(mvc.perform(post("/api/channels/{id}/posts", channelId).with(user(author, false)).with(xsrf(mvc))
            .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"" + body + "\"}")).andExpect(status().isCreated())), "$.id");
    }

    private String commentBy(UUID author, String postId, String body, String parentId) throws Exception {
        String parent = parentId == null ? "" : ",\"parentId\":\"" + parentId + "\"";
        return JsonPath.read(json(mvc.perform(post("/api/posts/{id}/comments", postId).with(user(author, false)).with(xsrf(mvc))
            .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"" + body + "\"" + parent + "}")).andExpect(status().isCreated())), "$.id");
    }

    private ResultActions editPost(UUID who, String postId, String json) throws Exception {
        return mvc.perform(patch("/api/posts/{id}", postId).with(user(who, false)).with(xsrf(mvc))
            .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions deletePost(UUID who, String postId) throws Exception {
        return mvc.perform(delete("/api/posts/{id}", postId).with(user(who, false)).with(xsrf(mvc)));
    }

    private ResultActions pin(UUID who, String postId, boolean pinned) throws Exception {
        return mvc.perform((pinned ? post("/api/posts/{id}/pin", postId) : delete("/api/posts/{id}/pin", postId))
            .with(user(who, false)).with(xsrf(mvc)));
    }

    private ResultActions editComment(UUID who, String commentId, String body) throws Exception {
        return mvc.perform(patch("/api/comments/{id}", commentId).with(user(who, false)).with(xsrf(mvc))
            .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"" + body + "\"}"));
    }

    private ResultActions deleteComment(UUID who, String commentId) throws Exception {
        return mvc.perform(delete("/api/comments/{id}", commentId).with(user(who, false)).with(xsrf(mvc)));
    }

    @Test
    void authorsEditAndDeleteTheirOwnPostsAndNobodyElsesWithoutModerating() throws Exception {
        String postId = postBy(ada, "Roses are red");

        editPost(ada, postId, "{\"title\":\"  A classic \",\"body\":\"Roses are red, violets are blue\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("A classic"))
            .andExpect(jsonPath("$.body").value("Roses are red, violets are blue"))
            .andExpect(jsonPath("$.editedAt").value(notNullValue()))
            .andExpect(jsonPath("$.canEdit").value(true));
        editPost(ada, postId, "{\"body\":\"  \"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EMPTY_POST"));
        editPost(bob, postId, "{\"body\":\"Mine now\"}").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_AUTHOR"));
        deletePost(bob, postId).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CANNOT_DELETE"));
        mvc.perform(get("/api/posts/{id}", postId).with(user(bob, false)))
            .andExpect(jsonPath("$.canEdit").value(false))
            .andExpect(jsonPath("$.canDelete").value(false))
            .andExpect(jsonPath("$.canPin").value(false));

        deletePost(ada, postId).andExpect(status().isNoContent());
        mvc.perform(get("/api/channels/{id}/posts", channelId).with(user(bob, false)))
            .andExpect(jsonPath("$.items[0].id").value(postId))                         // a tombstone, in its place
            .andExpect(jsonPath("$.items[0].deleted").value(true))
            .andExpect(jsonPath("$.items[0].deletedByModerator").value(false))
            .andExpect(jsonPath("$.items[0].body").value(nullValue()));
        editPost(ada, postId, "{\"body\":\"Back\"}").andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("POST_DELETED"));
        deletePost(ada, UUID.randomUUID().toString()).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("POST_NOT_FOUND"));
        assertThat(jdbc.queryForObject("select count(*) from forum.posts where id = ?::uuid", Integer.class, postId)).isOne();   // never hard-deleted
    }

    @Test
    void moderatorsPinAndRemoveAnyonesPosts() throws Exception {
        String rules = postBy(ada, "House rules");
        String spam = postBy(bob, "Buy cheap rhymes");

        mvc.perform(get("/api/posts/{id}", rules).with(user(founder, false)))
            .andExpect(jsonPath("$.canPin").value(true))
            .andExpect(jsonPath("$.canDelete").value(true))
            .andExpect(jsonPath("$.canEdit").value(false));                             // not the author
        pin(bob, rules, true).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CANNOT_PIN"));
        pin(founder, rules, true).andExpect(status().isNoContent());
        mvc.perform(get("/api/channels/{id}/posts/pinned", channelId).with(user(bob, false)))
            .andExpect(jsonPath("$[*].id", contains(rules)))
            .andExpect(jsonPath("$[0].pinned").value(true));

        deletePost(founder, spam).andExpect(status().isNoContent());
        mvc.perform(get("/api/posts/{id}", spam).with(user(bob, false)))
            .andExpect(jsonPath("$.deleted").value(true))
            .andExpect(jsonPath("$.deletedByModerator").value(true));

        pin(founder, rules, false).andExpect(status().isNoContent());
        mvc.perform(get("/api/channels/{id}/posts/pinned", channelId).with(user(bob, false))).andExpect(jsonPath("$", empty()));
        pin(founder, rules, true).andExpect(status().isNoContent());
        deletePost(ada, rules).andExpect(status().isNoContent());                       // deleting unpins
        mvc.perform(get("/api/channels/{id}/posts/pinned", channelId).with(user(bob, false))).andExpect(jsonPath("$", empty()));
        pin(founder, rules, true).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("POST_DELETED"));
    }

    @Test
    void commentsAreEditedByTheirAuthorAndRemovedByThemOrAModerator() throws Exception {
        String postId = postBy(ada, "Haiku night");
        String first = commentBy(ada, postId, "I will bring tea", null);
        String reply = commentBy(bob, postId, "I bring cake", first);

        editComment(bob, first, "Tea is boring").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_AUTHOR"));
        deleteComment(bob, first).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CANNOT_DELETE"));
        editComment(ada, first, "I will bring green tea")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.body").value("I will bring green tea"))
            .andExpect(jsonPath("$.editedAt").value(notNullValue()));
        mvc.perform(get("/api/posts/{id}/comments", postId).with(user(ada, false)))
            .andExpect(jsonPath("$.items[*].canEdit", contains(true, false)))
            .andExpect(jsonPath("$.items[*].canDelete", contains(true, false)));
        mvc.perform(get("/api/posts/{id}/comments", postId).with(user(founder, false)))
            .andExpect(jsonPath("$.items[*].canDelete", contains(true, true)));

        deleteComment(founder, reply).andExpect(status().isNoContent());
        deleteComment(ada, first).andExpect(status().isNoContent());
        mvc.perform(get("/api/posts/{id}/comments", postId).with(user(bob, false)))
            .andExpect(jsonPath("$.items[*].id", contains(first, reply)))                   // replies stay under a tombstone
            .andExpect(jsonPath("$.items[*].deleted", contains(true, true)))
            .andExpect(jsonPath("$.items[*].deletedByModerator", contains(false, true)))
            .andExpect(jsonPath("$.items[0].body").value(nullValue()));
        editComment(ada, first, "Back").andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("COMMENT_DELETED"));
        deleteComment(ada, UUID.randomUUID().toString()).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("COMMENT_NOT_FOUND"));
    }

    @Test
    void earlierErasAreReadOnlyForEveryone() throws Exception {
        String postId = postBy(ada, "Old verse");
        String commentId = commentBy(ada, postId, "Old note", null);
        jdbc.update("update club.clubs set current_era = current_era + 1 where id = ?", clubId);   // as after a revival

        editPost(ada, postId, "{\"body\":\"New verse\"}")
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("HISTORY_READ_ONLY"));
        deletePost(founder, postId).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("HISTORY_READ_ONLY"));
        pin(founder, postId, true).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("HISTORY_READ_ONLY"));
        editComment(ada, commentId, "New note").andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("HISTORY_READ_ONLY"));
        deleteComment(founder, commentId).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("HISTORY_READ_ONLY"));
        mvc.perform(get("/api/posts/{id}", postId).with(user(founder, false)))
            .andExpect(jsonPath("$.canEdit").value(false))
            .andExpect(jsonPath("$.canDelete").value(false))
            .andExpect(jsonPath("$.canPin").value(false));
        mvc.perform(get("/api/posts/{id}/comments", postId).with(user(ada, false)))
            .andExpect(jsonPath("$.items[0].canEdit").value(false));
    }
}
