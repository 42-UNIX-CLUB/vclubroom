package pt.vclubroom;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ClubroomApplicationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:8.8-alpine").withExposedPorts(6379);

    @Autowired
    MockMvc mvc;

//    @Test
//    void apiRequiresAuthentication() throws Exception {
//        mvc.perform(get("/api/me"))
//            .andExpect(status().isUnauthorized());
//    }
//
//    @Test
//    void healthAndApiDocsArePublic() throws Exception {
//        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
//        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
//    }
//
//    @Test
//    void currentUserExposesLoginAndRoles() throws Exception {
//        // Same shape the 42 login produces: the principal is named by the "login" attribute.
//        OAuth2User user = new DefaultOAuth2User(
//            List.of(new SimpleGrantedAuthority("ROLE_USER")), Map.of("login", "alice"), "login");
//
//        mvc.perform(get("/api/me").with(oauth2Login().oauth2User(user)))
//            .andExpect(status().isOk())
//            .andExpect(jsonPath("$.login").value("alice"))
//            .andExpect(jsonPath("$.roles[0]").value("ROLE_USER"));
//    }
}
