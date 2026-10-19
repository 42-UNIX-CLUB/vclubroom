package pt.vclubroom.club;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
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
import pt.vclubroom.space.api.SpaceAccessPolicy;

/**
 * Reviving an archived club (E14-S02) across the modules: the requests, the admin's decision, the new era, the
 * ownership handover (R-05), the timeline and the notifications.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RevivalIntegrationTests {

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
    UserRepository users;

    @Autowired
    TransactionTemplate tx;

    private UUID admin;
    private UUID founder;
    private UUID carol;
    private UUID dave;
    private UUID clubId;
    private UUID channelId;

    private UUID person(String name, PlatformRole role) {
        String login = name.toLowerCase().replace(' ', '-') + "-" + TestRequests.suffix();
        return users.save(User.createLocal(login, name, login + "@example.test", "{noop}x", role, Instant.now())).id();
    }

    private ResultActions send(MockHttpServletRequestBuilder request, UUID as, String json) throws Exception {
        return mvc.perform(request.with(user(as, as.equals(admin))).with(xsrf(mvc)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions read(String path, UUID as, Object... vars) throws Exception {
        return mvc.perform(get(path, vars).with(user(as, as.equals(admin))));
    }

    private String requestRevival(UUID as) throws Exception {
        return JsonPath.read(send(post("/api/clubs/{id}/revival-requests", clubId), as, "{\"motivation\":\"A new promo wants it back\"}")
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    /** An archived club: its founder (the owner), and carol, a member. */
    @BeforeEach
    void anArchivedClub() throws Exception {
        admin = person("Alice Admin", PlatformRole.ADMIN);
        founder = person("Dan Founder", PlatformRole.USER);
        carol = person("Carol Silva", PlatformRole.USER);
        dave = person("Dave Costa", PlatformRole.USER);
        clubId = clubs.requestCreation(new Actor(founder, false),
            new CreateClubCommand("Film " + TestRequests.suffix(), null, null, "Culture", List.of())).id();
        clubs.approve(new Actor(admin, true), clubId);
        await().atMost(Duration.ofSeconds(10)).until(() ->
            authorization.isOwner(clubId, founder) && !spaceAccess.readableForumChannels(clubId, new Actor(founder, false)).isEmpty());
        channelId = spaceAccess.readableForumChannels(clubId, new Actor(founder, false)).iterator().next();
        tx.executeWithoutResult(s -> clubRepository.findById(clubId).orElseThrow().changeJoinPolicy(JoinPolicy.OPEN, Instant.now()));
        send(post("/api/clubs/{id}/join", clubId), carol, "{}").andExpect(status().isCreated());
        clubs.archive(new Actor(admin, true), clubId, "The founders graduated");
    }

    @Test
    void aStudentRevivesTheClubAndBecomesItsOwner() throws Exception {
        String carolsRequest = requestRevival(carol);
        send(post("/api/clubs/{id}/revival-requests", clubId), carol, "{\"motivation\":\"Again\"}")
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REVIVAL_ALREADY_REQUESTED"));
        String davesRequest = requestRevival(dave);

        read("/api/admin/revival-requests", carol).andExpect(status().isForbidden());
        read("/api/admin/revival-requests", admin)
            .andExpect(jsonPath("$.items[?(@.clubId == '" + clubId + "')].requesterName", contains("Carol Silva", "Dave Costa")))
            .andExpect(jsonPath("$.items[?(@.id == '" + carolsRequest + "')].motivation", contains("A new promo wants it back")));

        send(post("/api/admin/revival-requests/{id}/approve", carolsRequest), admin, "")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.currentEra").value(2));
        send(post("/api/admin/revival-requests/{id}/approve", davesRequest), admin, "")       // BR-20: turned down
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("REVIVAL_NOT_PENDING"));
        read("/api/admin/revival-requests?status=REJECTED", admin)
            .andExpect(jsonPath("$.items[?(@.id == '" + davesRequest + "')].reviewNote", contains("Another revival request was approved.")));
        send(post("/api/clubs/{id}/revival-requests", clubId), dave, "{\"motivation\":\"Me too\"}")
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("CLUB_NOT_ARCHIVED"));

        // R-05: carol owns era 2; the founder stays, as a member
        await().atMost(Duration.ofSeconds(10)).until(() -> authorization.isOwner(clubId, carol) && !authorization.isOwner(clubId, founder));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> read("/api/clubs/{id}/timeline", carol, clubId)
            .andExpect(jsonPath("$.items[?(@.type == 'REVIVED')].era", contains(2)))
            .andExpect(jsonPath("$.items[?(@.type == 'REVIVED')].subject.displayName", contains("Carol Silva"))));
        send(post("/api/channels/{id}/posts", channelId), founder, "{\"title\":\"Still here\",\"body\":\"Happy to help\"}")
            .andExpect(status().isCreated());                                                // era 2 accepts writes again

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            read("/api/notifications", admin).andExpect(jsonPath("$.items[*].type", hasItem("CLUB_REVIVAL_REQUESTED")));
            read("/api/notifications", carol).andExpect(jsonPath("$.items[*].title", hasItem(endsWith(" is yours"))));
            read("/api/notifications", founder).andExpect(jsonPath("$.items[*].title", hasItems(
                endsWith(" is back"), startsWith("You are no longer an owner of"))));
        });
    }

    @Test
    void anAdminTurnsARequestDownWithAReason() throws Exception {
        send(post("/api/clubs/{id}/revival-requests", clubId), carol, "{\"motivation\":\"  \"}").andExpect(status().isBadRequest());
        String request = requestRevival(carol);
        send(post("/api/admin/revival-requests/{id}/reject", request), carol, "{\"reason\":\"No\"}").andExpect(status().isForbidden());

        send(post("/api/admin/revival-requests/{id}/reject", request), admin, "{\"reason\":\"Nobody to run the screenings yet\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REJECTED"))
            .andExpect(jsonPath("$.reviewNote").value("Nobody to run the screenings yet"));
        read("/api/clubs/{id}", carol, clubId).andExpect(jsonPath("$.status").value("ARCHIVED"));
        requestRevival(carol);                                                               // nothing pending: may ask again
    }
}
