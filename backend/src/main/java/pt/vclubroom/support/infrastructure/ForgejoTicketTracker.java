package pt.vclubroom.support.infrastructure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import pt.vclubroom.support.application.NewIssue;
import pt.vclubroom.support.application.ReporterReply;
import pt.vclubroom.support.application.TicketTracker;
import pt.vclubroom.support.application.TrackerChanges;
import pt.vclubroom.support.application.TrackerException;
import pt.vclubroom.support.domain.LinkedWork;

/**
 * Forgejo's REST API, as the bot account of scripts/tickets-setup.sh. Label IDs are looked up once and kept. Issues and
 * comments carry a hidden marker with their ticket's or message's ID: that is how a retry finds what was created after
 * all. The bot's own comments are flagged, so its copies of the reporters' replies are never read back as answers.
 * Linked work (E18-S03) is what a support issue depends on in the development repository, which the bot may only read.
 */
@Component
@ConditionalOnProperty(name = "app.support.tracker", havingValue = "forgejo")
class ForgejoTicketTracker implements TicketTracker {

    private static final int PAGE = 50;                               // Forgejo's largest page
    private static final ParameterizedTypeReference<List<Issue>> ISSUES = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<List<Comment>> COMMENTS = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<List<Label>> LABELS = new ParameterizedTypeReference<>() { };

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Issue(int number, String title, String body, String state, List<Label> labels, Milestone milestone, Repository repository) {

        List<String> labelNames() {
            return labels == null ? List.of() : labels.stream().map(Label::name).toList();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Milestone(String title, String state) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Repository(@JsonProperty("full_name") String fullName) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Comment(long id, String body, @JsonProperty("issue_url") String issueUrl, User user) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Label(long id, String name) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record User(String login) { }

    private final RestClient http;
    private final String repo;                                        // "/repos/vclubroom/support"
    private final String development;                                 // "vclubroom/vclubroom"
    private final Map<String, Long> labelIds = new ConcurrentHashMap<>();
    private volatile String botLogin;

    ForgejoTicketTracker(ForgejoProperties properties) {
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        requests.setReadTimeout(Duration.ofSeconds(10));               // never let the tracker hold the job up
        this.http = RestClient.builder()
            .baseUrl(properties.url().replaceAll("/+$", "") + "/api/v1")
            .defaultHeader(HttpHeaders.AUTHORIZATION, "token " + properties.token())
            .requestFactory(requests)
            .build();
        this.repo = "/repos/" + properties.repository();
        this.development = properties.development();
    }

    @Override
    public int createIssue(NewIssue issue) {
        String marker = "<!-- vclubroom-ticket:" + issue.ticketId() + " -->";
        return call(() -> {
            if (issue.retry()) {
                // The newest issues the bot created: a retry comes within the hour after its failed attempt.
                for (Issue existing : http.get().uri(repo + "/issues?state=all&type=issues&created_by={bot}&limit=" + PAGE, bot())
                        .retrieve().body(ISSUES)) {
                    if (existing.body() != null && existing.body().contains(marker)) {
                        return existing.number();
                    }
                }
            }
            return http.post().uri(repo + "/issues")
                .body(Map.of("title", issue.title(), "body", issue.body() + "\n" + marker,
                    "labels", List.of(labelId(ForgejoLabels.type(issue.type())), labelId(ForgejoLabels.FROM_APP))))
                .retrieve().body(Issue.class).number();
        });
    }

    @Override
    public long addReporterReply(int issue, ReporterReply reply) {
        String marker = "<!-- vclubroom-message:" + reply.messageId() + " -->";
        return call(() -> {
            Long id = null;
            if (reply.retry()) {
                id = pages(repo + "/issues/" + issue + "/comments?", COMMENTS).stream()
                    .filter(c -> c.body() != null && c.body().contains(marker)).map(Comment::id).findFirst().orElse(null);
            }
            if (id == null) {
                id = http.post().uri(repo + "/issues/{issue}/comments", issue)
                    .body(Map.of("body", reply.body() + "\n" + marker)).retrieve().body(Comment.class).id();
            }
            // They answered, so the team no longer waits for them. Removing a label the issue doesn't have changes nothing.
            http.delete().uri(repo + "/issues/{issue}/labels/{label}", issue, labelId(ForgejoLabels.WAITING))
                .retrieve().toBodilessEntity();
            return id;
        });
    }

    @Override
    public TrackerChanges changesSince(Instant since) {
        String from = since.truncatedTo(ChronoUnit.SECONDS).toString();
        return call(() -> {
            String bot = bot();
            List<TrackerChanges.Issue> issues = pages(repo + "/issues?state=all&type=issues&since=" + from + "&", ISSUES).stream()
                .map(i -> new TrackerChanges.Issue(i.number(), ForgejoLabels.status("open".equals(i.state()), i.labelNames()),
                    linkedWork(i.number())))
                .toList();
            List<TrackerChanges.Comment> comments = pages(repo + "/issues/comments?since=" + from + "&", COMMENTS).stream()
                .map(c -> new TrackerChanges.Comment(c.id(), issueNumber(c.issueUrl()), c.user() != null && bot.equals(c.user().login()), c.body()))
                .toList();
            List<LinkedWork> work = pages("/repos/" + development + "/issues?state=all&type=issues&since=" + from + "&", ISSUES)
                .stream().map(ForgejoTicketTracker::work).toList();
            return new TrackerChanges(issues, comments, work);
        });
    }

    /** The development issues a support issue depends on; dependencies elsewhere aren't shown to reporters. */
    private List<LinkedWork> linkedWork(int issue) {
        return http.get().uri(repo + "/issues/{issue}/dependencies", issue).retrieve().body(ISSUES).stream()
            .filter(d -> d.repository() != null && development.equals(d.repository().fullName()))
            .map(ForgejoTicketTracker::work).toList();
    }

    private static LinkedWork work(Issue i) {
        boolean released = i.milestone() != null && "closed".equals(i.milestone().state());
        return new LinkedWork(i.number(), i.title(), ForgejoLabels.work("open".equals(i.state()), i.labelNames()),
            i.milestone() == null ? null : i.milestone().title(), released);
    }

    /** ".../repos/vclubroom/support/issues/12" → 12. */
    private static int issueNumber(String issueUrl) {
        return Integer.parseInt(issueUrl.substring(issueUrl.lastIndexOf('/') + 1));
    }

    /** Every page of a list; uri ends with "?" or "&". */
    private <T> List<T> pages(String uri, ParameterizedTypeReference<List<T>> type) {
        List<T> all = new ArrayList<>();
        for (int page = 1; ; page++) {
            List<T> batch = http.get().uri(uri + "limit=" + PAGE + "&page=" + page).retrieve().body(type);
            all.addAll(batch);
            if (batch.size() < PAGE) {
                return all;
            }
        }
    }

    private String bot() {
        if (botLogin == null) {
            botLogin = http.get().uri("/user").retrieve().body(User.class).login();
        }
        return botLogin;
    }

    private long labelId(String name) {
        if (!labelIds.containsKey(name)) {
            pages(repo + "/labels?", LABELS).forEach(l -> labelIds.put(l.name(), l.id()));
        }
        Long id = labelIds.get(name);
        if (id == null) {
            throw new TrackerException("Label " + name + " is missing in " + repo.substring(7) + ": run scripts/tickets-setup.sh", null);
        }
        return id;
    }

    private interface Call<T> {
        T run();
    }

    private static <T> T call(Call<T> call) {
        try {
            return call.run();
        } catch (RestClientResponseException e) {
            throw new TrackerException("Forgejo answered " + e.getStatusCode().value() + " " + e.getStatusText(), e);
        } catch (RestClientException e) {
            throw new TrackerException("Forgejo unreachable: " + e.getMessage(), e);
        }
    }
}
