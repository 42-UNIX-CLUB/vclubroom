package pt.vclubroom.support.infrastructure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.net.http.HttpClient;
import java.time.Duration;
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
import pt.vclubroom.support.application.TicketTracker;
import pt.vclubroom.support.application.TrackerException;

/**
 * Forgejo's REST API, as the bot account of scripts/tickets-setup.sh. Label IDs are looked up once and kept. Each
 * issue carries a hidden marker with its ticket's ID: that is how a retry finds an issue that was created after all.
 */
@Component
@ConditionalOnProperty(name = "app.support.tracker", havingValue = "forgejo")
class ForgejoTicketTracker implements TicketTracker {

    private static final ParameterizedTypeReference<List<Issue>> ISSUES = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<List<Label>> LABELS = new ParameterizedTypeReference<>() { };

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Issue(int number, String body) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Label(long id, String name) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record User(String login) { }

    private final RestClient http;
    private final String repo;                                        // "/repos/vclubroom/support"
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
    }

    @Override
    public int createIssue(NewIssue issue) {
        String marker = "<!-- vclubroom-ticket:" + issue.ticketId() + " -->";
        return call(() -> {
            if (issue.retry()) {
                for (Issue existing : botIssues()) {
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

    /** The newest issues the bot created: a retry comes within the hour after its failed attempt. */
    private List<Issue> botIssues() {
        if (botLogin == null) {
            botLogin = http.get().uri("/user").retrieve().body(User.class).login();
        }
        return http.get().uri(repo + "/issues?state=all&type=issues&created_by={bot}&limit=50", botLogin).retrieve().body(ISSUES);
    }

    private long labelId(String name) {
        if (!labelIds.containsKey(name)) {
            List<Label> page;
            int number = 1;
            do {
                page = http.get().uri(repo + "/labels?limit=50&page={page}", number++).retrieve().body(LABELS);
                page.forEach(l -> labelIds.put(l.name(), l.id()));
            } while (page.size() == 50);
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
