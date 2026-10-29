package pt.vclubroom.group;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
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
import java.time.Instant;
import java.util.UUID;
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
import pt.vclubroom.identity.api.PlatformRole;
import pt.vclubroom.identity.domain.User;
import pt.vclubroom.identity.persistence.UserRepository;

/** Campus groups for admins over HTTP (E15-S03): manual ones are theirs to shape, synced ones are read-only. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CampusGroupAdminIntegrationTests {

    private static final UUID ADMIN = UUID.randomUUID();

    @Autowired
    MockMvc mvc;

    @Autowired
    UserRepository users;

    @Autowired
    JdbcTemplate jdbc;

    private ResultActions admin(MockHttpServletRequestBuilder request, String json) throws Exception {
        request = request.with(user(ADMIN, true)).with(xsrf(mvc));
        return mvc.perform(json == null ? request : request.contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private UUID person(String name) {
        String login = name.toLowerCase() + "-" + TestRequests.suffix();
        return users.save(User.createLocal(login, name, null, "{noop}x", PlatformRole.USER, Instant.now())).id();
    }

    @Test
    void adminsShapeManualGroupsThatEveryoneCanList() throws Exception {
        String name = "Tutors " + TestRequests.suffix();
        String id = JsonPath.read(admin(post("/api/admin/campus-groups"), "{\"name\":\"" + name + "\",\"description\":\"Peer tutors\"}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.synced").value(false))
            .andExpect(jsonPath("$.slug").value(startsWith("manual-tutors-")))
            .andReturn().getResponse().getContentAsString(), "$.id");
        admin(post("/api/admin/campus-groups"), "{\"name\":\"" + name.toUpperCase() + "\"}")
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("GROUP_NAME_TAKEN"));

        UUID ana = person("Ana");
        admin(put("/api/admin/campus-groups/{id}/members/{userId}", id, ana), null).andExpect(status().isNoContent());
        admin(put("/api/admin/campus-groups/{id}/members/{userId}", id, ana), null).andExpect(status().isNoContent());   // again: no change
        admin(put("/api/admin/campus-groups/{id}/members/{userId}", id, UUID.randomUUID()), null)
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        admin(get("/api/admin/campus-groups/{id}/members", id), null).andExpect(jsonPath("$[0].user.displayName").value("Ana"));

        admin(patch("/api/admin/campus-groups/{id}", id), "{\"name\":\"" + name + " (2026)\"}").andExpect(status().isOk());
        mvc.perform(get("/api/campus-groups").with(user(ana, false)))                     // every user, for the picker
            .andExpect(jsonPath("$[?(@.id == '" + id + "')].name").value(hasItem(name + " (2026)")))
            .andExpect(jsonPath("$[?(@.id == '" + id + "')].memberCount").value(hasItem(1)));
        mvc.perform(post("/api/admin/campus-groups").with(user(ana, false)).with(xsrf(mvc))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Mine\"}"))
            .andExpect(status().isForbidden());

        admin(delete("/api/admin/campus-groups/{id}/members/{userId}", id, ana), null).andExpect(status().isNoContent());
        admin(delete("/api/admin/campus-groups/{id}", id), null).andExpect(status().isNoContent());
        mvc.perform(get("/api/campus-groups").with(user(ana, false))).andExpect(jsonPath("$[*].id", not(hasItem(id))));
    }

    @Test
    void syncedGroupsAreReadOnly() throws Exception {
        String slug = "pool-1999-" + TestRequests.suffix();
        jdbc.update("insert into groups.groups (id, scope, name, slug, source, created_at) values (gen_random_uuid(), 'CAMPUS', 'Pool 1999', ?, 'SYNCED', now())", slug);
        String id = jdbc.queryForObject("select id::text from groups.groups where slug = ?", String.class, slug);

        admin(patch("/api/admin/campus-groups/{id}", id), "{\"name\":\"Renamed\"}")
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("GROUP_SYNCED"));
        admin(put("/api/admin/campus-groups/{id}/members/{userId}", id, person("Bob")), null)
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("GROUP_SYNCED"));
        admin(delete("/api/admin/campus-groups/{id}", id), null)
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("GROUP_SYNCED"));
        admin(get("/api/admin/campus-groups/{id}/members", id), null).andExpect(status().isOk());   // reading is fine
    }
}
