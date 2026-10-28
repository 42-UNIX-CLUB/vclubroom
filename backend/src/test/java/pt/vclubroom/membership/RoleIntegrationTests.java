package pt.vclubroom.membership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
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
import pt.vclubroom.membership.events.MemberRolesChanged;
import pt.vclubroom.shared.security.Actor;
import pt.vclubroom.space.api.SpaceAccessPolicy;

/** Custom club roles (E13-S02): the catalogue, CRUD, assignment, effective permissions and anti-escalation (BR-14). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class RoleIntegrationTests {

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
    ApplicationEvents events;

    @Autowired
    UserRepository users;

    private final UUID founder = UUID.randomUUID();
    private UUID ada;
    private UUID bob;
    private UUID clubId;
    private UUID generalSpace;

    private ResultActions send(MockHttpServletRequestBuilder request, UUID as, String json) throws Exception {
        return mvc.perform(request.with(user(as, false)).with(xsrf(mvc)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String createRole(UUID as, String name, String permissions) throws Exception {
        return JsonPath.read(send(post("/api/clubs/{id}/roles", clubId), as,
            "{\"name\":\"" + name + "\",\"color\":\"#10b981\",\"permissions\":[" + permissions + "]}")
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private ResultActions assign(UUID as, UUID member, String... roleIds) throws Exception {
        String ids = String.join(",", java.util.Arrays.stream(roleIds).map(id -> "\"" + id + "\"").toList());
        return send(put("/api/clubs/{id}/members/{userId}/roles", clubId, member), as, "{\"roleIds\":[" + ids + "]}");
    }

    private ResultActions scheduleEvent(UUID as) throws Exception {
        Instant start = Instant.now().plus(Duration.ofDays(2)).truncatedTo(ChronoUnit.SECONDS);
        return send(post("/api/clubs/{id}/events", clubId), as, """
            {"spaceId":"%s","title":"Blitz night","startsAt":"%s","endsAt":"%s"}""".formatted(generalSpace, start, start.plus(Duration.ofHours(2))));
    }

    private String roleId(String name) throws Exception {
        List<String> ids = JsonPath.read(mvc.perform(get("/api/clubs/{id}/roles", clubId).with(user(founder, false)))
            .andReturn().getResponse().getContentAsString(), "$[?(@.name == '" + name + "')].id");
        return ids.getFirst();
    }

    private UUID person(String name) {
        String login = name.toLowerCase().replace(' ', '.') + "-" + TestRequests.suffix();
        return users.save(User.createLocal(login, name, login + "@example.test", "{noop}x", PlatformRole.USER, Instant.now())).id();
    }

    /** An OPEN club: its founder (owner) and two members, ada and bob. */
    @BeforeEach
    void aClubWithMembers() throws Exception {
        ada = person("Ada Lopes");
        bob = person("Bob Martins");
        clubId = clubs.requestCreation(new Actor(founder, false),
            new CreateClubCommand("Chess " + TestRequests.suffix(), null, null, "Games", List.of())).id();
        clubs.approve(new Actor(UUID.randomUUID(), true), clubId);
        await().atMost(Duration.ofSeconds(10)).until(() -> authorization.isOwner(clubId, founder)
            && !spaceAccess.readableForumChannels(clubId, new Actor(founder, false)).isEmpty());
        generalSpace = UUID.fromString(JsonPath.read(mvc.perform(get("/api/clubs/{id}/spaces", clubId).with(user(founder, false)))
            .andReturn().getResponse().getContentAsString(), "$[0].id"));
        tx.executeWithoutResult(s -> clubRepository.findById(clubId).orElseThrow().changeJoinPolicy(JoinPolicy.OPEN, Instant.now()));
        for (UUID who : List.of(ada, bob)) {
            send(post("/api/clubs/{id}/join", clubId), who, "{}").andExpect(status().isCreated());
        }
    }

    @Test
    void anOwnerCreatesARoleAndItsHoldersCanDoWhatItAllows() throws Exception {
        mvc.perform(get("/api/permissions").with(user(ada, false)))
            .andExpect(jsonPath("$", hasSize(9)))
            .andExpect(jsonPath("$[?(@.code == 'EVENT_MANAGE')].label", contains("Manage events")));

        String eventLead = createRole(founder, "Event lead", "\"EVENT_MANAGE\"");
        send(post("/api/clubs/{id}/roles", clubId), founder, "{\"name\":\"event LEAD\",\"permissions\":[]}")
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ROLE_NAME_TAKEN"));
        mvc.perform(get("/api/clubs/{id}/roles", clubId).with(user(ada, false)))
            .andExpect(jsonPath("$[*].name", contains("Owner", "Event lead", "Member")))
            .andExpect(jsonPath("$[1].permissions", contains("EVENT_MANAGE")))
            .andExpect(jsonPath("$[2].memberCount").value(3));

        scheduleEvent(ada).andExpect(status().isForbidden());
        assign(founder, ada, eventLead).andExpect(status().isOk());
        assertThat(events.stream(MemberRolesChanged.class).filter(e -> e.userId().equals(ada))).hasSize(1);
        scheduleEvent(ada).andExpect(status().isCreated());                                   // effective at once
        mvc.perform(get("/api/clubs/{id}/members", clubId).param("roleId", eventLead).with(user(bob, false)))
            .andExpect(jsonPath("$.items[*].userId", contains(ada.toString())));

        send(delete("/api/clubs/{id}/roles/{roleId}", clubId, eventLead), founder, "").andExpect(status().isNoContent());
        scheduleEvent(ada).andExpect(status().isForbidden());
        mvc.perform(get("/api/clubs/{id}/members", clubId).param("query", "Ada Lopes").with(user(bob, false)))
            .andExpect(jsonPath("$.items[0].roles[*].name", contains("Member")));
    }

    @Test
    void theMemberRoleCanBeWidenedButSystemRolesStayAndOwnerIsUntouchable() throws Exception {
        String member = roleId("Member");
        String owner = roleId("Owner");
        send(patch("/api/clubs/{id}/roles/{roleId}", clubId, member), founder, "{\"permissions\":[\"EVENT_MANAGE\"]}")
            .andExpect(status().isOk()).andExpect(jsonPath("$.permissions", contains("EVENT_MANAGE")));
        scheduleEvent(bob).andExpect(status().isCreated());                                   // every member now

        send(patch("/api/clubs/{id}/roles/{roleId}", clubId, member), founder, "{\"name\":\"Fans\"}")
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("SYSTEM_ROLE_IMMUTABLE"));
        send(patch("/api/clubs/{id}/roles/{roleId}", clubId, owner), founder, "{\"permissions\":[]}")
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("SYSTEM_ROLE_IMMUTABLE"));
        send(delete("/api/clubs/{id}/roles/{roleId}", clubId, member), founder, "")
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("SYSTEM_ROLE_IMMUTABLE"));
        assign(founder, bob, owner)
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("ROLE_NOT_ASSIGNABLE"));
    }

    @Test
    void nobodyGrantsWhatTheyDoNotHold() throws Exception {
        String roleManager = createRole(founder, "Role manager", "\"ROLE_MANAGE\"");
        String eventLead = createRole(founder, "Event lead", "\"EVENT_MANAGE\"");
        send(post("/api/clubs/{id}/roles", clubId), ada, "{\"name\":\"Mine\",\"permissions\":[]}")
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));

        assign(founder, ada, roleManager).andExpect(status().isOk());                          // ada manages roles now
        send(post("/api/clubs/{id}/roles", clubId), ada, "{\"name\":\"Events too\",\"permissions\":[\"EVENT_MANAGE\"]}")
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_ESCALATION"));
        createRole(ada, "Helpers", "\"ROLE_MANAGE\"");                                         // within her own
        assign(ada, bob, eventLead)
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_ESCALATION"));
        send(patch("/api/clubs/{id}/roles/{roleId}", clubId, eventLead), ada, "{\"name\":\"Events\"}")
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_ESCALATION"));
        send(delete("/api/clubs/{id}/roles/{roleId}", clubId, eventLead), ada, "")
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_ESCALATION"));

        assign(founder, bob, eventLead).andExpect(status().isOk());
        assign(ada, bob).andExpect(status().isOk());                                          // taking away is fine
        scheduleEvent(bob).andExpect(status().isForbidden());
    }
}
