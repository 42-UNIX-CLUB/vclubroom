package pt.vclubroom.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pt.vclubroom.TestRequests.user;
import static pt.vclubroom.TestRequests.xsrf;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import pt.vclubroom.TestRequests;
import pt.vclubroom.TestcontainersConfiguration;
import pt.vclubroom.club.application.ClubService;
import pt.vclubroom.club.application.CreateClubCommand;
import pt.vclubroom.identity.api.PlatformRole;
import pt.vclubroom.identity.domain.User;
import pt.vclubroom.identity.persistence.UserRepository;
import pt.vclubroom.notification.domain.NotificationType;
import pt.vclubroom.shared.security.Actor;

/** My notification preferences over HTTP (E12-S02): defaults, overrides only, and the dispatcher honouring them. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PreferenceIntegrationTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    ClubService clubs;

    @Autowired
    UserRepository users;

    @Autowired
    JdbcTemplate jdbc;

    private UUID person(String name) {
        String login = name.toLowerCase() + "-" + TestRequests.suffix();
        return users.save(User.createLocal(login, name, login + "@example.test", "{noop}x", PlatformRole.USER, Instant.now())).id();
    }

    private int stored(UUID who) {
        return jdbc.queryForObject("select count(*) from notification.preferences where user_id = ?", Integer.class, who);
    }

    @Test
    void everyTypeShowsItsDefaultsAndOnlyOverridesAreStored() throws Exception {
        UUID me = person("Erin");
        mvc.perform(get("/api/me/notification-preferences").with(user(me, false)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(NotificationType.values().length)))
            .andExpect(jsonPath("$[?(@.type == 'CLUB_REJECTED')].inApp").value(true))
            .andExpect(jsonPath("$[?(@.type == 'CLUB_REJECTED')].email").value(true))
            .andExpect(jsonPath("$[?(@.type == 'NEW_POST')].email").value(false))
            .andExpect(jsonPath("$[?(@.type == 'NEW_POST')].defaultEmail").value(false));

        mvc.perform(put("/api/me/notification-preferences").with(user(me, false)).with(xsrf(mvc)).contentType(MediaType.APPLICATION_JSON)
                .content("""
                    [{"type":"NEW_POST","inApp":true,"email":true},
                     {"type":"CLUB_REJECTED","inApp":false,"email":false},
                     {"type":"EVENT_REMINDER","inApp":true,"email":true}]"""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.type == 'NEW_POST')].email").value(true))
            .andExpect(jsonPath("$[?(@.type == 'NEW_POST')].defaultEmail").value(false))
            .andExpect(jsonPath("$[?(@.type == 'CLUB_REJECTED')].inApp").value(false));
        assertThat(stored(me)).isEqualTo(2);                                      // EVENT_REMINDER is its defaults

        mvc.perform(put("/api/me/notification-preferences").with(user(me, false)).with(xsrf(mvc)).contentType(MediaType.APPLICATION_JSON)
                .content("[{\"type\":\"NEW_POST\",\"inApp\":true,\"email\":false}]"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.type == 'CLUB_REJECTED')].inApp").value(false));     // types not sent stay
        assertThat(stored(me)).isEqualTo(1);                                      // back to its defaults: removed

        mvc.perform(put("/api/me/notification-preferences").with(user(me, false)).with(xsrf(mvc)).contentType(MediaType.APPLICATION_JSON)
                .content("[{\"type\":\"NOT_A_TYPE\",\"inApp\":true,\"email\":false}]"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/me/notification-preferences")).andExpect(status().isUnauthorized());
    }

    @Test
    void aTypeTurnedOffNeverReachesTheInbox() throws Exception {
        UUID quiet = person("Frank");
        UUID loud = person("Grace");
        mvc.perform(put("/api/me/notification-preferences").with(user(quiet, false)).with(xsrf(mvc)).contentType(MediaType.APPLICATION_JSON)
                .content("[{\"type\":\"CLUB_REJECTED\",\"inApp\":false,\"email\":false}]"))
            .andExpect(status().isOk());

        for (UUID founder : List.of(quiet, loud)) {
            var club = clubs.requestCreation(new Actor(founder, false), new CreateClubCommand("Darts " + TestRequests.suffix(), null, null, "Games", List.of()));
            clubs.reject(new Actor(UUID.randomUUID(), true), club.id(), "Too close to the Pub Club");
        }
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> mvc.perform(get("/api/notifications").with(user(loud, false)))
            .andExpect(jsonPath("$.items[*].type", hasItem("CLUB_REJECTED"))));
        await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).untilAsserted(() -> mvc.perform(get("/api/notifications").with(user(quiet, false)))
            .andExpect(jsonPath("$.items[*].type", not(hasItem("CLUB_REJECTED")))));
    }
}
