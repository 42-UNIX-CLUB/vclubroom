package pt.vclubroom.support.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
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
import pt.vclubroom.support.application.ReporterReply;
import pt.vclubroom.support.application.TrackerChanges;
import pt.vclubroom.support.application.TrackerException;
import pt.vclubroom.support.domain.LinkedWork;
import pt.vclubroom.support.domain.TicketStatus;
import pt.vclubroom.support.domain.TicketType;
import pt.vclubroom.support.domain.WorkState;

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
    private static String owner;                                      // sets things up
    private static String bot;                                        // the app's account
    private static String ada;                                        // someone of the team

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

    /** What scripts/tickets-setup.sh does, in short: the repositories, some of their labels, the app's account, a team member. */
    @BeforeAll
    static void setUp() throws Exception {
        owner = token("owner", "all");
        bot = token("bot", "write:issue,read:repository,read:user", "--restricted");
        ada = token("ada", "all");
        api(owner, "POST", "/user/repos", "{\"name\":\"support\",\"private\":true}");
        api(owner, "PUT", "/repos/owner/support/collaborators/bot", "{\"permission\":\"write\"}");
        api(owner, "POST", "/user/repos", "{\"name\":\"development\",\"private\":true}");          // E18-S03
        api(owner, "PUT", "/repos/owner/development/collaborators/bot", "{\"permission\":\"read\"}");
        api(owner, "PUT", "/repos/owner/development/collaborators/ada", "{\"permission\":\"write\"}");
        api(owner, "POST", "/repos/owner/development/labels", "{\"name\":\"status/in-progress\",\"color\":\"#fbca04\",\"exclusive\":true}");
        api(owner, "POST", "/user/repos", "{\"name\":\"elsewhere\",\"private\":true}");
        api(owner, "PUT", "/repos/owner/support/collaborators/ada", "{\"permission\":\"write\"}");
        api(owner, "POST", "/repos/owner/support/labels", "{\"name\":\"source/app\",\"color\":\"#ededed\"}");
        for (String label : List.of("type/bug", "status/in-progress", "status/waiting-on-reporter", "resolution/wont-fix")) {
            api(owner, "POST", "/repos/owner/support/labels", "{\"name\":\"%s\",\"color\":\"#cccccc\",\"exclusive\":true}".formatted(label));
        }
    }

    private static ForgejoTicketTracker tracker(String url, String token) {
        return new ForgejoTicketTracker(new ForgejoProperties(url, token, "owner/support", "owner/development"));
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
    void aReplyBecomesACommentAndTheIssueStopsWaiting() throws Exception {
        ForgejoTicketTracker tracker = tracker(url(), bot);
        int issue = tracker.createIssue(issue(UUID.randomUUID(), TicketType.BUG, false));
        api(ada, "PUT", "/repos/owner/support/issues/" + issue + "/labels", "{\"labels\":[\"status/waiting-on-reporter\"]}");
        ReporterReply reply = new ReporterReply(UUID.randomUUID(), "**Ada** replied in the app: Firefox", false);

        long comment = tracker.addReporterReply(issue, reply);

        assertThat(tracker.addReporterReply(issue, new ReporterReply(reply.messageId(), reply.body(), true))).isEqualTo(comment);
        assertThat(JsonPath.<List<Integer>>read(api(bot, "GET", "/repos/owner/support/issues/" + issue + "/comments", null), "$[*].id"))
            .hasSize(1);                                                   // the retry found it
        assertThat(JsonPath.<List<String>>read(api(bot, "GET", "/repos/owner/support/issues/" + issue, null), "$.labels[*].name"))
            .doesNotContain("status/waiting-on-reporter");
    }

    @Test
    void readsStatusesAndCommentsSinceAMoment() throws Exception {
        ForgejoTicketTracker tracker = tracker(url(), bot);
        Instant before = Instant.now().minusSeconds(5);
        int working = tracker.createIssue(issue(UUID.randomUUID(), TicketType.BUG, false));
        int declined = tracker.createIssue(issue(UUID.randomUUID(), TicketType.BUG, false));
        api(ada, "PUT", "/repos/owner/support/issues/" + working + "/labels", "{\"labels\":[\"status/in-progress\"]}");
        api(ada, "POST", "/repos/owner/support/issues/" + working + "/comments", "{\"body\":\"/reply On it!\"}");
        tracker.addReporterReply(working, new ReporterReply(UUID.randomUUID(), "**Ada** replied in the app: thanks", false));
        api(ada, "PUT", "/repos/owner/support/issues/" + declined + "/labels", "{\"labels\":[\"resolution/wont-fix\"]}");
        api(ada, "PATCH", "/repos/owner/support/issues/" + declined, "{\"state\":\"closed\"}");

        TrackerChanges changes = tracker.changesSince(before);

        assertThat(changes.issues()).extracting(TrackerChanges.Issue::number, TrackerChanges.Issue::status)
            .contains(tuple(working, TicketStatus.IN_PROGRESS), tuple(declined, TicketStatus.DECLINED));
        assertThat(changes.comments()).filteredOn(c -> c.issue() == working)
            .extracting(TrackerChanges.Comment::byTracker, c -> c.body().lines().findFirst().orElse(""))
            .containsExactly(tuple(false, "/reply On it!"), tuple(true, "**Ada** replied in the app: thanks"));
        assertThat(tracker.changesSince(Instant.now().plusSeconds(60)).issues()).isEmpty();
    }

    @Test
    void aTicketsLinkedWorkIsWhatItsIssueDependsOnInTheDevelopmentRepository() throws Exception {
        ForgejoTicketTracker tracker = tracker(url(), bot);
        Instant before = Instant.now().minusSeconds(5);
        int support = tracker.createIssue(issue(UUID.randomUUID(), TicketType.BUG, false));
        String release = api(owner, "POST", "/repos/owner/development/milestones", "{\"title\":\"v1.2.0\"}");
        int fix = JsonPath.read(api(owner, "POST", "/repos/owner/development/issues",
            "{\"title\":\"E09-S04 · Edit, delete, pin and moderate\",\"milestone\":%d}".formatted(JsonPath.<Integer>read(release, "$.id"))), "$.number");
        int other = JsonPath.read(api(owner, "POST", "/repos/owner/elsewhere/issues", "{\"title\":\"Not development\"}"), "$.number");
        api(ada, "PUT", "/repos/owner/development/issues/" + fix + "/labels", "{\"labels\":[\"status/in-progress\"]}");
        api(owner, "POST", "/repos/owner/support/issues/" + support + "/dependencies", "{\"owner\":\"owner\",\"repo\":\"development\",\"index\":%d}".formatted(fix));
        api(owner, "POST", "/repos/owner/support/issues/" + support + "/dependencies", "{\"owner\":\"owner\",\"repo\":\"elsewhere\",\"index\":%d}".formatted(other));

        TrackerChanges changes = tracker.changesSince(before);

        assertThat(changes.issues()).filteredOn(i -> i.number() == support).singleElement().satisfies(i -> assertThat(i.links())
            .containsExactly(new LinkedWork(fix, "E09-S04 · Edit, delete, pin and moderate", WorkState.IN_PROGRESS, "v1.2.0", false)));
        api(owner, "PATCH", "/repos/owner/development/issues/" + fix, "{\"state\":\"closed\"}");
        api(owner, "PATCH", "/repos/owner/development/milestones/" + JsonPath.<Integer>read(release, "$.id"), "{\"state\":\"closed\"}");
        assertThat(tracker.changesSince(before).work()).filteredOn(w -> w.issue() == fix).singleElement()
            .isEqualTo(new LinkedWork(fix, "E09-S04 · Edit, delete, pin and moderate", WorkState.DONE, "v1.2.0", true));
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
