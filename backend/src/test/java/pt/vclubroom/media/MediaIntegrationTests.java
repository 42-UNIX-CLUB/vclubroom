package pt.vclubroom.media;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pt.vclubroom.TestRequests.user;
import static pt.vclubroom.TestRequests.xsrf;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import pt.vclubroom.TestcontainersConfiguration;

/** POST /api/media/uploads over HTTP: validation and the presigned URL (signing is local, no storage needed). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MediaIntegrationTests {

    @Autowired
    MockMvc mvc;

    private final UUID student = UUID.randomUUID();

    private ResultActions slot(String json) throws Exception {
        return mvc.perform(post("/api/media/uploads").with(user(student, false)).with(xsrf(mvc))
            .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void aSlotIsAPresignedPutOnTheAppsOwnOrigin() throws Exception {
        slot("{\"purpose\":\"POST_ATTACHMENT\",\"fileName\":\"cat.png\",\"contentType\":\"image/png\",\"sizeBytes\":1000}")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.mediaId").value(notNullValue()))
            .andExpect(jsonPath("$.method").value("PUT"))
            .andExpect(jsonPath("$.uploadUrl").value(startsWith("http://localhost:5173/media-private/post_attachment/")))
            .andExpect(jsonPath("$.headers.content-type", contains("image/png")));
        slot("{\"purpose\":\"CLUB_AVATAR\",\"fileName\":\"logo.png\",\"contentType\":\"image/png\",\"sizeBytes\":1000}")
            .andExpect(jsonPath("$.uploadUrl").value(startsWith("http://localhost:5173/media-public/club_avatar/")));
    }

    @Test
    void unsupportedTypesAndOversizedFilesAreRefused() throws Exception {
        slot("{\"purpose\":\"POST_ATTACHMENT\",\"fileName\":\"a.txt\",\"contentType\":\"text/plain\",\"sizeBytes\":10}")
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("MEDIA_TYPE_NOT_ALLOWED"));
        slot("{\"purpose\":\"POST_ATTACHMENT\",\"fileName\":\"long.mp4\",\"contentType\":\"video/mp4\",\"sizeBytes\":104857600}")
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("MEDIA_TOO_LARGE"));
        slot("{\"fileName\":\"cat.png\",\"contentType\":\"image/png\",\"sizeBytes\":1000}")
            .andExpect(status().isBadRequest());
    }

    @Test
    void onlyTheUploaderCompletesAndVisitorsMustLogIn() throws Exception {
        mvc.perform(post("/api/media/{id}/complete", UUID.randomUUID()).with(user(student, false)).with(xsrf(mvc)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("MEDIA_NOT_FOUND"));
        mvc.perform(post("/api/media/uploads").with(xsrf(mvc)).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized());
    }
}
