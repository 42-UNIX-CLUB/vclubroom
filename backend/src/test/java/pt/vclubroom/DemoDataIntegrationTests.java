package pt.vclubroom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import pt.vclubroom.club.api.ClubCatalog;
import pt.vclubroom.club.api.ClubSnapshot;
import pt.vclubroom.club.api.ClubStatus;
import pt.vclubroom.club.api.JoinPolicy;
import pt.vclubroom.identity.api.UserDirectory;
import pt.vclubroom.membership.api.ClubAuthorization;

/**
 * The demo profile seeds an empty database (E16-S03), and a second run adds nothing. This test has its own
 * context (the demo profile), hence its own containers: the other tests' data is not affected.
 */
@SpringBootTest
@ActiveProfiles("demo")
@Import(TestcontainersConfiguration.class)
class DemoDataIntegrationTests {

    @Autowired
    UserDirectory users;

    @Autowired
    ClubCatalog clubs;

    @Autowired
    ClubAuthorization authorization;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    List<ApplicationRunner> runners;

    private ClubSnapshot club(String slug) {
        return clubs.findBySlug(slug).orElseThrow();
    }

    private long count(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    /** Everything the seeders create, counted; equal after a second run. */
    private Map<String, Long> totals() {
        return Map.of(
            "users", count("select count(*) from identity.users"),
            "clubs", count("select count(*) from club.clubs"),
            "members", count("select count(*) from membership.memberships"),
            "requests", count("select count(*) from membership.join_requests"),
            "posts", count("select count(*) from forum.posts"),
            "comments", count("select count(*) from forum.comments"),
            "messages", count("select count(*) from chat.messages"),
            "events", count("select count(*) from calendar.events"),
            "revivals", count("select count(*) from club.revival_requests"));
    }

    @Test
    void anEmptyDatabaseIsSeededOnceAndOnlyOnce() throws Exception {
        assertThat(List.of("alice", "bob", "carol", "dave")).allSatisfy(login -> assertThat(users.findByLogin(login)).isPresent());
        assertThat(users.adminIds()).contains(users.findByLogin("alice").orElseThrow().id());

        assertThat(List.of("chess-club", "42-robotics", "photo-club", "hiking-club", "game-dev-club"))
            .allSatisfy(slug -> assertThat(club(slug).status()).isEqualTo(ClubStatus.ACTIVE));
        assertThat(club("chess-club").joinPolicy()).isEqualTo(JoinPolicy.OPEN);
        ClubSnapshot film = club("film-society");
        assertThat(film.status()).isEqualTo(ClubStatus.ARCHIVED);
        assertThat(film.currentEra()).isEqualTo(2);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
            assertThat(authorization.activeMemberIds(club("chess-club").id())).hasSize(3));
        UUID dave = users.findByLogin("dave").orElseThrow().id();
        assertThat(count("select count(*) from membership.join_requests where status = 'PENDING' and user_id = '" + dave + "'")).isEqualTo(1);
        assertThat(count("select count(*) from forum.posts where club_id = '" + film.id() + "' and era = 2")).isEqualTo(1);
        UUID carol = users.findByLogin("carol").orElseThrow().id();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {                   // revived by carol (E14-S02)
            assertThat(authorization.isOwner(film.id(), carol)).isTrue();
            assertThat(authorization.isOwner(film.id(), dave)).isFalse();
            assertThat(count("select count(*) from history.timeline_entries where club_id = '" + film.id() + "' and type = 'REVIVED'")).isEqualTo(1);
        });
        assertThat(count("select count(*) from chat.messages")).isEqualTo(5);
        assertThat(count("select count(*) from calendar.events where status = 'SCHEDULED'")).isEqualTo(3);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(
            jdbc.queryForObject("select join_policy from discovery.club_cards where slug = 'chess-club'", String.class)).isEqualTo("OPEN"));

        Map<String, Long> before = totals();
        AnnotationAwareOrderComparator.sort(runners);
        for (ApplicationRunner runner : runners) {
            runner.run(new DefaultApplicationArguments());
        }
        Supplier<Map<String, Long>> after = this::totals;
        assertThat(after.get()).isEqualTo(before);
    }
}
