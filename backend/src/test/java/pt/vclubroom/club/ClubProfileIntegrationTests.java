package pt.vclubroom.club;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pt.vclubroom.TestRequests.user;
import static pt.vclubroom.TestRequests.xsrf;

import java.sql.Timestamp;
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
import pt.vclubroom.TestRequests;
import pt.vclubroom.TestcontainersConfiguration;
import pt.vclubroom.club.application.ClubService;
import pt.vclubroom.club.application.CreateClubCommand;
import pt.vclubroom.membership.api.ClubAuthorization;
import pt.vclubroom.shared.security.Actor;

/** Club settings › Profile (E04-S04): who may edit, partial updates, the join policy, and the images. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ClubProfileIntegrationTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    ClubService clubs;

    @Autowired
    ClubAuthorization authorization;

    @Autowired
    JdbcTemplate jdbc;

    private final UUID admin = UUID.randomUUID();
    private final UUID founder = UUID.randomUUID();
    private final UUID student = UUID.randomUUID();
    private UUID clubId;
    private String slug;

    private ResultActions send(MockHttpServletRequestBuilder request, UUID as, String json) throws Exception {
        return mvc.perform(request.with(user(as, as.equals(admin))).with(xsrf(mvc)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions edit(UUID as, String json) throws Exception {
        return send(patch("/api/clubs/{id}", clubId), as, json);
    }

    /** An uploaded, verified file, as media leaves it after "complete" (the test setup has no object storage). */
    private UUID readyUpload(UUID owner, String purpose) {
        UUID id = UUID.randomUUID();
        String bucket = purpose.startsWith("CLUB_") ? "media-public" : "media-private";
        jdbc.update("""
            insert into media.media_objects (id, owner_id, purpose, kind, content_type, size_bytes, bucket, storage_key, status, created_at)
            values (?, ?, ?, 'IMAGE', 'image/png', 1000, ?, ?, 'READY', ?)""",
            id, owner, purpose, bucket, purpose.toLowerCase() + "/" + id + ".png", Timestamp.from(Instant.now()));
        return id;
    }

    private String cardColumn(String column) {
        return jdbc.queryForObject("select " + column + " from discovery.club_cards where club_id = ?", String.class, clubId);
    }

    @BeforeEach
    void anActiveClub() {
        var club = clubs.requestCreation(new Actor(founder, false),
            new CreateClubCommand("Photo " + TestRequests.suffix(), "Golden hour", "Walks", "Arts", List.of("photography")));
        clubId = club.id();
        slug = club.slug();
        clubs.approve(new Actor(admin, true), clubId);
        await().atMost(Duration.ofSeconds(10)).until(() -> authorization.isOwner(clubId, founder));
    }

    @Test
    void theOwnerChangesOnlyWhatTheySendAndOthersCannot() throws Exception {
        edit(founder, "{\"tagline\":\"Every season\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tagline").value("Every season"))
            .andExpect(jsonPath("$.description").value("Walks"))
            .andExpect(jsonPath("$.tags[0]").value("photography"))
            .andExpect(jsonPath("$.slug").value(slug));
        edit(founder, "{\"name\":\"Lens Club\"}").andExpect(jsonPath("$.name").value("Lens Club")).andExpect(jsonPath("$.slug").value(slug));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(cardColumn("name")).isEqualTo("Lens Club"));

        edit(student, "{\"tagline\":\"Mine now\"}").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        edit(founder, "{\"category\":\"Astrology\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CATEGORY"));
        edit(founder, "{\"name\":\"ab\"}").andExpect(status().isBadRequest());
    }

    @Test
    void switchingToOpenLetsEveryoneWaitingIn() throws Exception {
        send(post("/api/clubs/{id}/join", clubId), student, "{}").andExpect(status().isAccepted());   // APPROVAL: a request

        edit(founder, "{\"joinPolicy\":\"OPEN\"}").andExpect(status().isOk()).andExpect(jsonPath("$.joinPolicy").value("OPEN"));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(authorization.activeMemberIds(clubId)).contains(student);
            assertThat(cardColumn("join_policy")).isEqualTo("OPEN");
        });
        edit(student, "{\"joinPolicy\":\"APPROVAL\"}").andExpect(status().isForbidden());          // a member, not a manager
    }

    @Test
    void imagesAreTheEditorsOwnUploadsAndReachTheDirectory() throws Exception {
        UUID someoneElses = readyUpload(student, "CLUB_BANNER");
        UUID wrongPurpose = readyUpload(founder, "POST_ATTACHMENT");
        edit(founder, "{\"bannerMediaId\":\"" + someoneElses + "\"}")
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("MEDIA_NOT_ATTACHABLE"));
        edit(founder, "{\"avatarMediaId\":\"" + wrongPurpose + "\"}")
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("MEDIA_NOT_ATTACHABLE"));

        UUID avatar = readyUpload(founder, "CLUB_AVATAR");
        edit(founder, "{\"avatarMediaId\":\"" + avatar + "\"}")
            .andExpect(status().isOk()).andExpect(jsonPath("$.avatarUrl").value(endsWith("/media-public/club_avatar/" + avatar + ".png")));
        mvc.perform(get("/api/clubs/{slug}", slug).with(user(student, false)))
            .andExpect(jsonPath("$.avatarUrl").value(endsWith(avatar + ".png")))
            .andExpect(jsonPath("$.bannerUrl").doesNotExist());
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(cardColumn("avatar_url")).endsWith(avatar + ".png"));
        assertThat(jdbc.queryForObject("select status from media.media_objects where id = ?", String.class, avatar)).isEqualTo("ATTACHED");
    }

    @Test
    void aPendingClubIsTheFoundersAndAnArchivedOneIsNobodys() throws Exception {
        var pending = clubs.requestCreation(new Actor(founder, false),
            new CreateClubCommand("Draft " + TestRequests.suffix(), null, null, "Arts", List.of()));
        send(patch("/api/clubs/{id}", pending.id()), founder, "{\"tagline\":\"Fixed a typo\"}")
            .andExpect(status().isOk()).andExpect(jsonPath("$.tagline").value("Fixed a typo"));
        send(patch("/api/clubs/{id}", pending.id()), student, "{\"tagline\":\"Hi\"}").andExpect(status().isForbidden());
        send(patch("/api/clubs/{id}", pending.id()), founder, "{\"joinPolicy\":\"OPEN\"}")
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("CLUB_NOT_ACTIVE"));

        clubs.archive(new Actor(admin, true), clubId, "Paused");
        edit(founder, "{\"tagline\":\"Still here\"}").andExpect(status().isForbidden());
    }
}
