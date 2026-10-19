package pt.vclubroom.forum;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
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
import org.springframework.test.web.servlet.ResultActions;
import pt.vclubroom.TestRequests;
import pt.vclubroom.TestcontainersConfiguration;
import pt.vclubroom.club.application.ClubService;
import pt.vclubroom.club.application.CreateClubCommand;
import pt.vclubroom.discovery.domain.ClubCard;
import pt.vclubroom.discovery.persistence.ClubCardRepository;
import pt.vclubroom.media.api.MediaKind;
import pt.vclubroom.media.api.MediaPurpose;
import pt.vclubroom.media.domain.MediaObject;
import pt.vclubroom.media.persistence.MediaObjectRepository;
import pt.vclubroom.membership.api.ClubAuthorization;
import pt.vclubroom.shared.security.Actor;
import pt.vclubroom.space.api.SpaceAccessPolicy;

/** Posts over HTTP in a real club's "Posts" channel (E09-S01), and what the rest of the platform sees. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PostIntegrationTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    ClubService clubs;

    @Autowired
    ClubAuthorization authorization;

    @Autowired
    SpaceAccessPolicy spaceAccess;

    @Autowired
    MediaObjectRepository media;

    @Autowired
    ClubCardRepository cards;

    private final UUID founder = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();
    private UUID clubId;
    private UUID posts;

    @BeforeEach
    void aClubWithItsPostsChannel() {
        clubId = clubs.requestCreation(new Actor(founder, false),
            new CreateClubCommand("Photography " + TestRequests.suffix(), null, null, "Arts", List.of())).id();
        clubs.approve(new Actor(UUID.randomUUID(), true), clubId);
        await().atMost(Duration.ofSeconds(10)).until(() ->
            authorization.isOwner(clubId, founder) && !spaceAccess.readableForumChannels(clubId, new Actor(founder, false)).isEmpty());
        posts = spaceAccess.readableForumChannels(clubId, new Actor(founder, false)).iterator().next();
    }

    private ResultActions publish(UUID author, String json) throws Exception {
        return mvc.perform(post("/api/channels/{id}/posts", posts).with(user(author, false)).with(xsrf(mvc))
            .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** An uploaded, verified file of this user (the upload itself is E08-S02's business). */
    private UUID readyPhoto(UUID owner) {
        MediaObject photo = MediaObject.pending(owner, MediaPurpose.POST_ATTACHMENT, MediaKind.IMAGE, "image/png", 1000,
            "photo.png", "media-private", Instant.now());
        photo.markReady(1000, "image/png");
        return media.save(photo).id();
    }

    @Test
    void aMemberPublishesTextAndMediaAndEveryReaderSeesItOnTop() throws Exception {
        UUID photo = readyPhoto(founder);
        publish(founder, "{\"body\":\"First!\"}").andExpect(status().isCreated());
        publish(founder, "{\"title\":\"Sunset\",\"body\":\"From the roof\",\"mediaIds\":[\"" + photo + "\"]}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.media[0].kind").value("IMAGE"))
            .andExpect(jsonPath("$.media[0].url").value(startsWith("http://localhost:5173/media-private/")))
            .andExpect(jsonPath("$.era").value(1));

        mvc.perform(get("/api/channels/{id}/posts", posts).with(user(stranger, false)))      // a PUBLIC space
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].body", contains("From the roof", "First!")))
            .andExpect(jsonPath("$.items[0].title").value("Sunset"))
            .andExpect(jsonPath("$.items[0].readOnly").value(true))                         // a reader: no reactions or comments
            .andExpect(jsonPath("$.nextCursor").value(nullValue()));
    }

    @Test
    void readersCannotPostAndEmptyPostsAreRefused() throws Exception {
        publish(stranger, "{\"body\":\"Hello\"}")
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("CHANNEL_WRITE_DENIED"));
        publish(founder, "{\"body\":\"   \",\"mediaIds\":[]}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("EMPTY_POST"));
        publish(founder, "{\"body\":\"Not mine\",\"mediaIds\":[\"" + readyPhoto(stranger) + "\"]}")
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("MEDIA_NOT_ATTACHABLE"));                   // BR-19
    }

    @Test
    void olderPostsComePageByPage() throws Exception {
        for (int i = 1; i <= 3; i++) {
            publish(founder, "{\"body\":\"Post " + i + "\"}").andExpect(status().isCreated());
        }
        String first = mvc.perform(get("/api/channels/{id}/posts", posts).param("limit", "2").with(user(founder, false)))
            .andExpect(jsonPath("$.items[*].body", contains("Post 3", "Post 2")))
            .andExpect(jsonPath("$.nextCursor").value(notNullValue()))
            .andReturn().getResponse().getContentAsString();

        mvc.perform(get("/api/channels/{id}/posts", posts).param("limit", "2")
                .param("cursor", JsonPath.<String>read(first, "$.nextCursor")).with(user(founder, false)))
            .andExpect(jsonPath("$.items[*].body", contains("Post 1")))
            .andExpect(jsonPath("$.nextCursor").value(nullValue()));
        mvc.perform(get("/api/channels/{id}/posts", posts).param("cursor", "not-a-cursor").with(user(founder, false)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_CURSOR"));
    }

    @Test
    void anEraFilterShowsOnlyThatEra() throws Exception {
        publish(founder, "{\"body\":\"In era one\"}").andExpect(status().isCreated());
        mvc.perform(get("/api/channels/{id}/posts", posts).param("era", "1").with(user(founder, false)))
            .andExpect(jsonPath("$.items", hasSize(1)));
        mvc.perform(get("/api/channels/{id}/posts", posts).param("era", "2").with(user(founder, false)))
            .andExpect(jsonPath("$.items", empty()));
    }

    @Test
    void aPostMakesTheClubLookActiveInTheDirectory() throws Exception {
        publish(founder, "{\"body\":\"Activity!\"}").andExpect(status().isCreated());
        await().atMost(Duration.ofSeconds(10)).until(() ->
            cards.findById(clubId).map(ClubCard::lastActivityAt).isPresent());
    }
}
