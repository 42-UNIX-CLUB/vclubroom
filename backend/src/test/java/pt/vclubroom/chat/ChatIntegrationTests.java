package pt.vclubroom.chat;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.jdbc.core.JdbcTemplate;
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
import pt.vclubroom.membership.api.ClubAuthorization;
import pt.vclubroom.shared.security.Actor;

/** Chat over HTTP in a real club's Lounge (E10-S02). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ChatIntegrationTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    ClubService clubs;

    @Autowired
    ClubRepository clubRepository;

    @Autowired
    ClubAuthorization authorization;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcTemplate jdbc;

    private final UUID founder = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();
    private UUID clubId;
    private String lounge;

    /** An OPEN club with a member; its General space has the Lounge chat channel (E05-S01). */
    @BeforeEach
    void aClubWithItsLounge() throws Exception {
        clubId = clubs.requestCreation(new Actor(founder, false),
            new CreateClubCommand("Go " + TestRequests.suffix(), null, null, "Games", List.of())).id();
        clubs.approve(new Actor(UUID.randomUUID(), true), clubId);
        await().atMost(Duration.ofSeconds(10)).until(() -> authorization.isOwner(clubId, founder));
        tx.executeWithoutResult(s -> clubRepository.findById(clubId).orElseThrow().changeJoinPolicy(JoinPolicy.OPEN, Instant.now()));
        send(post("/api/clubs/{id}/join", clubId), member, "{}").andExpect(status().isCreated());
        String spaces = mvc.perform(get("/api/clubs/{id}/spaces", clubId).with(user(founder, false)))
            .andReturn().getResponse().getContentAsString();
        lounge = JsonPath.<List<String>>read(spaces, "$[0].channels[?(@.type == 'CHAT')].id").getFirst();
    }

    private ResultActions send(MockHttpServletRequestBuilder request, UUID as, String json) throws Exception {
        return mvc.perform(request.with(user(as, false)).with(xsrf(mvc)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions say(UUID as, String body, UUID nonce) throws Exception {
        return send(post("/api/channels/{id}/messages", lounge), as, "{\"body\":\"" + body + "\",\"clientNonce\":\"" + nonce + "\"}");
    }

    private long idOf(ResultActions result) throws Exception {
        return ((Number) JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    @Test
    void historyComesOldestFirstPageByPageAndRetriesNeverDuplicate() throws Exception {
        UUID nonce = UUID.randomUUID();
        long first = idOf(say(founder, "hi", nonce).andExpect(status().isCreated()));
        say(member, "hello", UUID.randomUUID()).andExpect(status().isCreated());
        say(founder, "ready?", UUID.randomUUID()).andExpect(status().isCreated());
        say(founder, "hi", nonce)                                                       // the same send, retried
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(first));

        String page = mvc.perform(get("/api/channels/{id}/messages", lounge).param("limit", "2").with(user(stranger, false)))
            .andExpect(status().isOk())                                                  // a PUBLIC space: readable
            .andExpect(jsonPath("$.items[*].body", contains("hello", "ready?")))
            .andExpect(jsonPath("$.nextBefore").value(notNullValue()))
            .andReturn().getResponse().getContentAsString();
        mvc.perform(get("/api/channels/{id}/messages", lounge).param("limit", "2")
                .param("before", String.valueOf(((Number) JsonPath.read(page, "$.nextBefore")).longValue())).with(user(stranger, false)))
            .andExpect(jsonPath("$.items[*].body", contains("hi")))
            .andExpect(jsonPath("$.nextBefore").value(nullValue()));
    }

    @Test
    void onlyWritersSendAndMessagesNeedText() throws Exception {
        say(stranger, "let me in", UUID.randomUUID())
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CHANNEL_WRITE_DENIED"));
        say(member, "  ", UUID.randomUUID())
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EMPTY_MESSAGE"));
        send(post("/api/channels/{id}/messages", lounge), member, "{\"body\":\"no nonce\"}").andExpect(status().isBadRequest());
    }

    @Test
    void authorsEditTheirOwnAndModeratorsDelete() throws Exception {
        long mine = idOf(say(member, "gg", UUID.randomUUID()));
        long theirs = idOf(say(founder, "rematch?", UUID.randomUUID()));

        send(patch("/api/messages/{id}", mine), member, "{\"body\":\"good game\"}")
            .andExpect(status().isOk()).andExpect(jsonPath("$.body").value("good game")).andExpect(jsonPath("$.editedAt").value(notNullValue()));
        send(patch("/api/messages/{id}", theirs), member, "{\"body\":\"no\"}")
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("CANNOT_EDIT"));
        send(delete("/api/messages/{id}", theirs), member, "")
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CANNOT_DELETE"));
        send(delete("/api/messages/{id}", mine), founder, "")                             // the owner moderates
            .andExpect(status().isOk()).andExpect(jsonPath("$.deleted").value(true)).andExpect(jsonPath("$.body").value(nullValue()));
    }

    @Test
    void earlierErasAreReadOnlyAndArchivedClubsTooForSending() throws Exception {
        long old = idOf(say(member, "era one", UUID.randomUUID()));
        jdbc.update("update club.clubs set current_era = current_era + 1 where id = ?", clubId);   // as after a revival

        send(patch("/api/messages/{id}", old), member, "{\"body\":\"edited later\"}")
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("HISTORY_READ_ONLY"));
        mvc.perform(get("/api/channels/{id}/messages", lounge).param("era", "1").with(user(member, false)))
            .andExpect(jsonPath("$.items[*].body", contains("era one")));
        jdbc.update("update club.clubs set current_era = current_era - 1 where id = ?", clubId);
        clubs.archive(new Actor(UUID.randomUUID(), true), clubId, "Done");
        say(member, "anyone?", UUID.randomUUID())
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CHANNEL_WRITE_DENIED"));
    }
}
