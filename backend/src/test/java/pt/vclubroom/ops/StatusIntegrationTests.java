package pt.vclubroom.ops;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pt.vclubroom.TestcontainersConfiguration;

/**
 * The public status (E19-S02), without logging in. The tests have PostgreSQL and Redis but no SeaweedFS: file storage
 * is down, so the platform is degraded, not down.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class StatusIntegrationTests {

    @Autowired
    MockMvc mvc;

    @Test
    void anyoneSeesEachPartAndThePlatformAsAWhole() throws Exception {
        String first = mvc.perform(get("/api/public/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.services[*].id").value(contains("app", "database", "sessions", "storage", "email", "tickets")))
            .andExpect(jsonPath("$.services[?(@.id == 'database')].status").value(contains("OPERATIONAL")))
            .andExpect(jsonPath("$.services[?(@.id == 'sessions')].status").value(contains("OPERATIONAL")))
            .andExpect(jsonPath("$.services[?(@.id == 'storage')].status").value(contains("DOWN")))
            .andExpect(jsonPath("$.status").value("DEGRADED"))
            .andReturn().getResponse().getContentAsString();

        mvc.perform(get("/api/public/status"))                                  // within 10 s: the same check
            .andExpect(jsonPath("$.checkedAt").value(JsonPath.<String>read(first, "$.checkedAt")));
    }
}
