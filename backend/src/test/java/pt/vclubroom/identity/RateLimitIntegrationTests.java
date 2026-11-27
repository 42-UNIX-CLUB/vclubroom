package pt.vclubroom.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static pt.vclubroom.TestRequests.bearer;
import static pt.vclubroom.TestRequests.user;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import pt.vclubroom.TestcontainersConfiguration;
import pt.vclubroom.identity.api.ApiThrottling;
import pt.vclubroom.identity.api.PlatformRole;
import pt.vclubroom.identity.domain.PersonalAccessToken;
import pt.vclubroom.identity.domain.User;
import pt.vclubroom.identity.persistence.PersonalAccessTokenRepository;
import pt.vclubroom.identity.persistence.UserRepository;
import pt.vclubroom.shared.security.RateLimitProperties;

/**
 * Rate limits (E20-S03): per token, per token for changes, per person, and per IP without logging in. Buckets refill
 * as the test runs, so a few extra requests may pass before the 429.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RateLimitIntegrationTests {

    private static final int REFILL_MARGIN = 5;

    @Autowired
    MockMvc mvc;

    @Autowired
    UserRepository users;

    @Autowired
    PersonalAccessTokenRepository tokens;

    @Autowired
    RateLimitProperties limits;

    @Autowired
    ApiThrottling throttling;

    @Test
    void aTokenHasItsBurstThenGetsA429WithRetryAfter() throws Exception {
        String token = token(account(), "profile:read");

        mvc.perform(get("/api/me").with(bearer(token)))
            .andExpect(status().isOk())
            .andExpect(header().string("RateLimit-Limit", Integer.toString(limits.token().burst())))
            .andExpect(header().string("RateLimit-Remaining", Integer.toString(limits.token().burst() - 1)))
            .andExpect(header().exists("RateLimit-Reset"));
        int passed = 1 + passedBefore429(get("/api/me").with(bearer(token)));

        assertThat(passed).isBetween(limits.token().burst(), limits.token().burst() + REFILL_MARGIN);
        mvc.perform(get("/api/me").with(bearer(token)))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
            .andExpect(header().exists("Retry-After"))
            .andExpect(header().string("RateLimit-Remaining", "0"));
        assertThat(throttling.refusedLastHour()).isPositive();                  // for the health card
    }

    @Test
    void changesHaveTheirOwnSmallerBucket() throws Exception {
        String token = token(account(), "notifications:read", "notifications:write");

        int writes = passedBefore429(post("/api/notifications/read-all").with(bearer(token)));

        assertThat(writes).isBetween(limits.tokenWrites().burst(), limits.tokenWrites().burst() + 1);
        mvc.perform(get("/api/notifications").with(bearer(token))).andExpect(status().isOk());       // reading goes on
    }

    @Test
    void aPersonsTokensShareOneBucket() throws Exception {
        User owner = account();
        int perToken = limits.token().burst();
        int tokensToEmptyIt = limits.user().burst() / perToken;
        for (int i = 0; i < tokensToEmptyIt; i++) {
            passedBefore429(get("/api/me").with(bearer(token(owner, "profile:read"))));
        }

        assertThat(passedBefore429(get("/api/me").with(bearer(token(owner, "profile:read"))))).isLessThan(REFILL_MARGIN);
        assertThat(passedBefore429(get("/api/me").with(bearer(token(account(), "profile:read"))))).isGreaterThanOrEqualTo(perToken);
    }

    @Test
    void anonymousVisitorsAreLimitedPerIpButNotLocallyNorWhenLoggedIn() throws Exception {
        int passed = passedBefore429(get("/api/public/categories").with(from("203.0.113.9")));

        assertThat(passed).isBetween(limits.anonymous().burst(), limits.anonymous().burst() + REFILL_MARGIN);
        mvc.perform(get("/api/public/categories").with(from("203.0.113.9")).with(user(account().id(), false)))
            .andExpect(status().isOk());
        mvc.perform(get("/api/public/categories").with(from("203.0.113.10"))).andExpect(status().isOk());
        for (int i = 0; i <= limits.anonymous().burst(); i++) {
            mvc.perform(get("/api/public/categories"))                        // 127.0.0.1: this machine
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("RateLimit-Limit"));
        }
    }

    /** How many requests pass before the first 429 (at most a few hundred). */
    private int passedBefore429(MockHttpServletRequestBuilder request) throws Exception {
        for (int i = 0; i < 300; i++) {
            if (mvc.perform(request).andReturn().getResponse().getStatus() == 429) {
                return i;
            }
        }
        throw new AssertionError("never limited");
    }

    private static RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private User account() {
        String login = "rl-" + UUID.randomUUID().toString().substring(0, 8);
        return users.save(User.createLocal(login, "Rate " + login, login + "@example.test", "{noop}secret", PlatformRole.USER, Instant.now()));
    }

    private String token(User owner, String... scopes) {
        String raw = PersonalAccessToken.PREFIX + UUID.randomUUID().toString().replace("-", "");
        tokens.save(PersonalAccessToken.issue(owner.id(), "test", Set.of(scopes), null, raw, Instant.now()));
        return raw;
    }
}
