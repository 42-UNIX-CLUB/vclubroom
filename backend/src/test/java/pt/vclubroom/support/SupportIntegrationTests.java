package pt.vclubroom.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pt.vclubroom.TestRequests.user;
import static pt.vclubroom.TestRequests.xsrf;

import com.jayway.jsonpath.JsonPath;
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
import pt.vclubroom.TestcontainersConfiguration;

/** Sending, reading and answering your own tickets over HTTP (E17-S02, E17-S03). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SupportIntegrationTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    private String browserOf(long number) {
        return jdbc.queryForObject("select user_agent from support.tickets where number = ?", String.class, number);
    }

    private ResultActions submit(UUID who, String json) throws Exception {
        return mvc.perform(post("/api/support/tickets").with(user(who, false)).with(xsrf(mvc))
            .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private long number(UUID who, String type, String title) throws Exception {
        String body = submit(who, """
            {"type":"%s","title":"%s","description":"Steps: open it, click it.","pageUrl":"/c/chess?tab=events",
             "userAgent":"Mozilla/5.0 (X11; Linux x86_64) Firefox/140.0"}""".formatted(type, title))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.number")).longValue();
    }

    @Test
    void aTicketIsReceivedWithItsPageAndOnlyABugKeepsTheBrowser() throws Exception {
        UUID ada = UUID.randomUUID();
        long bug = number(ada, "BUG", "Login button does nothing");
        long idea = number(ada, "SUGGESTION", "Dark mode for the calendar");

        mvc.perform(get("/api/support/tickets/{n}", bug).with(user(ada, false)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ref").value("T-" + bug))
            .andExpect(jsonPath("$.type").value("BUG"))
            .andExpect(jsonPath("$.status").value("RECEIVED"))
            .andExpect(jsonPath("$.pageUrl").value("/c/chess?tab=events"));
        mvc.perform(get("/api/support/tickets").with(user(ada, false)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].number").value(contains((int) idea, (int) bug)));          // newest first
        assertThat(browserOf(bug)).contains("Firefox/140.0");
        assertThat(browserOf(idea)).isNull();
    }

    @Test
    void anAddressOutsideTheAppIsNotKept() throws Exception {
        UUID ada = UUID.randomUUID();
        String body = submit(ada, """
            {"type":"QUESTION","title":"Where is it?","description":"I found this link.","pageUrl":"https://evil.example/x"}""")
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/support/tickets/{n}", ((Number) JsonPath.read(body, "$.number")).longValue()).with(user(ada, false)))
            .andExpect(jsonPath("$.pageUrl").value(nullValue()));
    }

    @Test
    void someoneElsesTicketDoesNotExist() throws Exception {
        long ticket = number(UUID.randomUUID(), "ACCOUNT", "I can't join Chess");

        mvc.perform(get("/api/support/tickets/{n}", ticket).with(user(UUID.randomUUID(), false)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("TICKET_NOT_FOUND"));
    }

    @Test
    void aTicketNeedsATypeATitleAndADescription() throws Exception {
        submit(UUID.randomUUID(), "{\"type\":\"BUG\",\"title\":\" \",\"description\":\"Broken\"}")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        submit(UUID.randomUUID(), "{\"title\":\"Broken\",\"description\":\"Broken\"}").andExpect(status().isBadRequest());
    }

    private ResultActions reply(UUID who, long ticket, String body) throws Exception {
        return mvc.perform(post("/api/support/tickets/{n}/messages", ticket).with(user(who, false)).with(xsrf(mvc))
            .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"%s\"}".formatted(body)));
    }

    @Test
    void theReporterAnswersWhileTheTicketIsOpen() throws Exception {
        UUID ada = UUID.randomUUID();
        long ticket = number(ada, "BUG", "Login button does nothing");
        jdbc.update("update support.tickets set tracker_issue = ?, status = 'WAITING_ON_REPORTER' where number = ?", (int) ticket, ticket);

        reply(ada, ticket, "Firefox 140, on Linux").andExpect(status().isCreated())
            .andExpect(jsonPath("$.author").value("REPORTER"))
            .andExpect(jsonPath("$.delivered").value(false));                  // the tracker gets it with the next sync
        mvc.perform(get("/api/support/tickets/{n}", ticket).with(user(ada, false)))
            .andExpect(jsonPath("$.status").value("OPEN"))                     // no longer waiting for them
            .andExpect(jsonPath("$.canReply").value(true))
            .andExpect(jsonPath("$.messages[*].body").value(contains("Firefox 140, on Linux")));
        reply(UUID.randomUUID(), ticket, "Not mine").andExpect(status().isNotFound());
        reply(ada, ticket, " ").andExpect(status().isBadRequest());
    }

    @Test
    void aClosedTicketTakesNoMoreReplies() throws Exception {
        UUID ada = UUID.randomUUID();
        long ticket = number(ada, "QUESTION", "How do I leave a club?");
        jdbc.update("update support.tickets set tracker_issue = ?, status = 'RESOLVED' where number = ?", (int) ticket, ticket);

        mvc.perform(get("/api/support/tickets/{n}", ticket).with(user(ada, false))).andExpect(jsonPath("$.canReply").value(false));
        reply(ada, ticket, "One more thing").andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("TICKET_CLOSED"));
    }

    @Test
    void tenTicketsADayAtMost() throws Exception {
        UUID busy = UUID.randomUUID();
        for (int i = 0; i < 10; i++) {
            number(busy, "OTHER", "Ticket " + i);
        }
        submit(busy, "{\"type\":\"OTHER\",\"title\":\"One more\",\"description\":\"Again\"}")
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("TICKET_LIMIT_REACHED"));
    }
}
