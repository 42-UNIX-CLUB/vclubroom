package pt.vclubroom.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import pt.vclubroom.TestcontainersConfiguration;

/** The API documentation as production serves it (E19-S01): public, without the admin endpoints. Its own context (prod). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@Import(TestcontainersConfiguration.class)
class PublicApiDocsIntegrationTests {

    @Autowired
    MockMvc mvc;

    @Test
    void everyoneReadsTheDocumentationExceptTheAdminEndpoints() throws Exception {
        String docs = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        Map<String, Object> paths = JsonPath.read(docs, "$.paths");
        assertThat(paths).containsKeys("/api/clubs", "/api/support/tickets", "/api/public/landing");
        assertThat(paths.keySet()).noneMatch(path -> path.startsWith("/api/admin"));
        assertThat(JsonPath.<String>read(docs, "$.info.title")).isEqualTo("Virtual Clubroom API");
        assertThat(JsonPath.<Map<String, Object>>read(docs, "$.components.securitySchemes")).containsKeys("session", "csrf", "bearer");
        assertThat(JsonPath.<List<String>>read(docs, "$.paths['/api/me'].get.security[1].bearer")).containsExactly("profile:read");
        assertThat(JsonPath.<String>read(docs, "$.paths['/api/clubs'].post.description")).contains("Token scope: `clubs:write`.");
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
