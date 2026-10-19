package pt.vclubroom.space;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.Scenario;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import pt.vclubroom.TestcontainersConfiguration;
import pt.vclubroom.club.api.ClubCatalog;
import pt.vclubroom.club.events.ClubApproved;
import pt.vclubroom.group.api.GroupMembershipQuery;
import pt.vclubroom.membership.api.ClubAuthorization;
import pt.vclubroom.space.persistence.SpaceRepository;

/** The space module started on its own: the default space of an approved club (E05-S01-T4). */
@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
class DefaultSpaceModuleTests {

    @MockitoBean
    ClubCatalog clubCatalog;                   // other modules' APIs: mocked, those modules are not started

    @MockitoBean
    ClubAuthorization clubAuthorization;

    @MockitoBean
    GroupMembershipQuery groupMembership;

    @Autowired
    SpaceRepository spaces;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    CompletedEventPublications completed;

    private static ClubApproved approved(UUID clubId) {
        return new ClubApproved(clubId, "chess-" + clubId, "Chess Club", UUID.randomUUID(), UUID.randomUUID(), 1, Instant.now());
    }

    /** One line per space, e.g. "General PUBLIC/CLUB_MEMBERS Posts:FORUM Lounge:CHAT" (channels load lazily, hence tx). */
    private List<String> spacesOf(UUID clubId) {
        return tx.execute(status -> spaces.findByClubIdOrderByPositionAsc(clubId).stream()
            .map(s -> s.name() + " " + s.readRule().level() + "/" + s.writeRule().level()
                + s.channels().stream().map(c -> " " + c.name() + ":" + c.type()).reduce("", String::concat))
            .toList());
    }

    @Test
    void anApprovedClubGetsTheGeneralSpaceWithPostsAndLounge(Scenario scenario) {
        UUID clubId = UUID.randomUUID();

        scenario.publish(approved(clubId))
            .andWaitForStateChange(() -> spacesOf(clubId), list -> !list.isEmpty())
            .andVerify(list -> assertThat(list).containsExactly("General PUBLIC/CLUB_MEMBERS Posts:FORUM Lounge:CHAT"));
    }

    @Test
    void processingTheSameEventTwiceCreatesNothingNew(Scenario scenario) {
        UUID clubId = UUID.randomUUID();
        ClubApproved event = approved(clubId);

        scenario.publish(event).andWaitForStateChange(() -> spacesOf(clubId), list -> !list.isEmpty());
        scenario.publish(event)
            .andWaitForStateChange(() -> handled(clubId), times -> times == 2)
            .andVerify(times -> assertThat(spacesOf(clubId)).hasSize(1));
    }

    private long handled(UUID clubId) {
        return completed.findAll().stream()
            .filter(publication -> publication.getEvent() instanceof ClubApproved e && e.clubId().equals(clubId))
            .count();
    }
}
