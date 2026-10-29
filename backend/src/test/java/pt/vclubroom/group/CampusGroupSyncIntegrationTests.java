package pt.vclubroom.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import pt.vclubroom.TestRequests;
import pt.vclubroom.TestcontainersConfiguration;
import pt.vclubroom.identity.application.UserProvisioningService;
import pt.vclubroom.identity.domain.FortyTwoProfile;

/** Campus groups synced at every 42 login (E13-S04), through the real provisioning: created once, joined and left. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CampusGroupSyncIntegrationTests {

    @Autowired
    UserProvisioningService provisioning;

    @Autowired
    JdbcTemplate jdbc;

    private static FortyTwoProfile profile(long ftId, String login, String month, List<String> campuses, List<String> cursus, boolean alumni) {
        return new FortyTwoProfile(ftId, login, null, "A Student", null, 2025, month, alumni, false, campuses, cursus);
    }

    /** The synced campus groups this person is in, by slug. */
    private List<String> groupsOf(UUID userId) {
        return jdbc.queryForList("""
            select g.slug from groups.group_members m join groups.groups g on g.id = m.group_id
            where m.user_id = ? and g.source = 'SYNCED' order by g.slug""", String.class, userId);
    }

    @Test
    void aLoginPutsTheStudentInTheGroupsOfTheirProfileAndTheNextOneFollowsChanges() {
        long ftId = System.nanoTime();
        String login = "sync-" + TestRequests.suffix();
        UUID id = provisioning.provisionFromFortyTwo(profile(ftId, login, "september", List.of("Lisboa"), List.of("42cursus"), false)).id();
        await().atMost(Duration.ofSeconds(10)).until(() -> groupsOf(id).size() == 3);
        assertThat(groupsOf(id)).containsExactly("campus-lisboa", "cursus-42cursus", "pool-2025-september");
        assertThat(jdbc.queryForObject("select name from groups.groups where slug = 'pool-2025-september'", String.class))
            .isEqualTo("Pool September 2025");

        provisioning.provisionFromFortyTwo(profile(ftId, login, "september", List.of("Lisboa", "Porto"), List.of("42cursus"), true));
        await().atMost(Duration.ofSeconds(10)).until(() -> groupsOf(id).size() == 5);
        assertThat(groupsOf(id)).containsExactly("alumni", "campus-lisboa", "campus-porto", "cursus-42cursus", "pool-2025-september");

        provisioning.provisionFromFortyTwo(profile(ftId, login, "september", List.of("Porto"), List.of(), true));   // moved campus
        await().atMost(Duration.ofSeconds(10)).until(() -> groupsOf(id).size() == 3);
        assertThat(groupsOf(id)).containsExactly("alumni", "campus-porto", "pool-2025-september");
    }

    @Test
    void groupsAreSharedAndManualOnesAreLeftAlone() {
        jdbc.update("""
            insert into groups.groups (id, scope, name, slug, source, created_at) values (gen_random_uuid(), 'CAMPUS', 'Tutors', ?, 'MANUAL', now())
            on conflict do nothing""", "cursus-tutors");                                 // a manual group with a synced-looking slug
        UUID ada = provisioning.provisionFromFortyTwo(profile(System.nanoTime(), "ada-" + TestRequests.suffix(), "march",
            List.of("Lisboa"), List.of("tutors"), false)).id();
        UUID bob = provisioning.provisionFromFortyTwo(profile(System.nanoTime(), "bob-" + TestRequests.suffix(), "march",
            List.of("Lisboa"), List.of(), false)).id();

        await().atMost(Duration.ofSeconds(10)).until(() -> groupsOf(ada).size() == 2 && groupsOf(bob).size() == 2);
        assertThat(groupsOf(ada)).containsExactly("campus-lisboa", "pool-2025-march");      // not the manual "cursus-tutors"
        assertThat(jdbc.queryForObject("select count(*) from groups.groups where slug = 'campus-lisboa'", Integer.class)).isOne();
        assertThat(jdbc.queryForObject("""
            select count(*) from groups.group_members m join groups.groups g on g.id = m.group_id where g.slug = 'cursus-tutors'""",
            Integer.class)).isZero();
    }
}
