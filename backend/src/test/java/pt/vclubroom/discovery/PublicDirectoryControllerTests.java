package pt.vclubroom.discovery;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import pt.vclubroom.TestcontainersConfiguration;

/** Same annotations as ClubroomApplicationTests, so Spring reuses its cached context and containers. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PublicDirectoryControllerTests {

    @Autowired
    MockMvc mvc;

    @Test
    void categoriesArePublicAndComeFromTheConfiguration() throws Exception {
        mvc.perform(get("/api/public/categories"))   // no session, no CSRF: anonymous GET
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", contains(
                "Tech", "Games", "Arts", "Music", "Sports", "Culture", "Social", "Science", "Other")));
    }
}
