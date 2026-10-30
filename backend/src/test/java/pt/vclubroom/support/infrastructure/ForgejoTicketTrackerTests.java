package pt.vclubroom.support.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pt.vclubroom.support.application.NewIssue;
import pt.vclubroom.support.application.TrackerException;
import pt.vclubroom.support.domain.TicketType;

/**
 * The Forgejo adapter against a real Forgejo (same image as compose.prod.yaml), without starting Spring. Its account is
 * the one scripts/tickets-setup.sh makes: restricted, a collaborator of the repository only, with the same token scopes.
 */
@Testcontainers
class ForgejoTicketTrackerTests {

    @Container
    static final GenericContainer<?> forgejo = new GenericContainer<>("codeberg.org/forgejo/forgejo:15.0.11-rootless")
        .withEnv("FORGEJO__database__DB_TYPE", "sqlite3")
        .withEnv("FORGEJO__security__INSTALL_LOCK", "true")
        .withEnv("FORGEJO__service__REQUIRE_SIGNIN_VIEW", "true")
        .withExposedPorts(3000)
        .waitingFor(Wait.forHttp("/api/healthz").forPort(3000));

    private static final HttpClient http = HttpClient.newHttpClient();
    private static String bot;                                        // the app's account

    private static String url() {
        return "http://" + forgejo.getHost() + ":" + forgejo.getMappedPort(3000);
    }

    private static String token(String user, String scopes, String... options) throws Exception {
        List<String> create = new ArrayList<>(List.of("forgejo", "admin", "user", "create", "--username", user,
            "--email", user + "@noreply.invalid", "--random-password", "--must-change-password=false"));
        create.addAll(List.of(options));
        forgejo.execInContainer(create.toArray(String[]::new));
        return forgejo.execInContainer("forgejo", "admin", "user", "generate-access-token", "--username", user,
            "--scopes", scopes, "--raw").getStdout().strip();
    }

    private static String api(String token, String method, String path, String json) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url() + "/api/v1" + path))
            .header("Authorization", "token " + token).header("Content-Type", "application/json")
            .method(method, json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString()).body();
    }

    /** What scripts/tickets-setup.sh does, in short: the repository, two of its labels, the app's account. */
    @BeforeAll
    static void setUp() throws Exception {
        String owner = token("owner", "all");
        bot = token("bot", "write:issue,read:repository,read:user", "--restricted");
        api(owner, "POST", "/user/repos", "{\"name\":\"support\",\"private\":true}");
        api(owner, "PUT", "/repos/owner/support/collaborators/bot", "{\"permission\":\"write\"}");
        api(owner, "POST", "/repos/owner/support/labels", "{\"name\":\"type/bug\",\"color\":\"#d73a4a\",\"exclusive\":true}");
        api(owner, "POST", "/repos/owner/support/labels", "{\"name\":\"source/app\",\"color\":\"#ededed\"}");
    }

    private static ForgejoTicketTracker tracker(String url, String token) {
        return new ForgejoTicketTracker(new ForgejoProperties(url, token, "owner/support"));
    }

    private static NewIssue issue(UUID ticketId, TicketType type, boolean retry) {
        return new NewIssue(ticketId, 17, type, "T-17 Login button does nothing", "**T-17** · Bug", retry);
    }

    @Test
    void createsTheIssueWithItsTypeAndSourceLabels() throws Exception {
        int number = tracker(url(), bot).createIssue(issue(UUID.randomUUID(), TicketType.BUG, false));

        String created = api(bot, "GET", "/repos/owner/support/issues/" + number, null);
        assertThat(JsonPath.<String>read(created, "$.title")).isEqualTo("T-17 Login button does nothing");
        assertThat(JsonPath.<List<String>>read(created, "$.labels[*].name")).containsExactlyInAnyOrder("type/bug", "source/app");
        assertThat(JsonPath.<String>read(created, "$.body")).startsWith("**T-17** · Bug").contains("<!-- vclubroom-ticket:");
    }

    @Test
    void aRetryFindsTheIssueAnEarlierAttemptCreated() {
        UUID ticket = UUID.randomUUID();
        ForgejoTicketTracker tracker = tracker(url(), bot);
        int first = tracker.createIssue(issue(ticket, TicketType.BUG, false));    // its response got lost

        assertThat(tracker.createIssue(issue(ticket, TicketType.BUG, true))).isEqualTo(first);
        assertThat(tracker.createIssue(issue(UUID.randomUUID(), TicketType.BUG, true))).isNotEqualTo(first);
    }

    @Test
    void aMissingLabelPointsToTheSetupScript() {
        assertThatThrownBy(() -> tracker(url(), bot).createIssue(issue(UUID.randomUUID(), TicketType.PRIVACY, false)))
            .isInstanceOf(TrackerException.class)
            .hasMessage("Label type/privacy is missing in owner/support: run scripts/tickets-setup.sh");
    }

    @Test
    void refusalsAndOutagesBecomeTrackerExceptions() {
        assertThatThrownBy(() -> tracker(url(), "wrong-token").createIssue(issue(UUID.randomUUID(), TicketType.BUG, false)))
            .isInstanceOf(TrackerException.class).hasMessage("Forgejo answered 401 Unauthorized");
        assertThatThrownBy(() -> tracker("http://localhost:1", bot).createIssue(issue(UUID.randomUUID(), TicketType.BUG, false)))
            .isInstanceOf(TrackerException.class).hasMessageStartingWith("Forgejo unreachable:");
    }
}
