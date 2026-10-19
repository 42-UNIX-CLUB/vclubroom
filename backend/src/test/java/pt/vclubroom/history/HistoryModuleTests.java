package pt.vclubroom.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Limit;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.Scenario;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pt.vclubroom.TestcontainersConfiguration;
import pt.vclubroom.club.api.ClubCatalog;
import pt.vclubroom.club.api.ClubSnapshot;
import pt.vclubroom.club.api.ClubStatus;
import pt.vclubroom.club.api.JoinPolicy;
import pt.vclubroom.club.events.ClubApproved;
import pt.vclubroom.history.domain.TimelineEntry;
import pt.vclubroom.history.domain.TimelineEntryType;
import pt.vclubroom.history.persistence.TimelineEntryRepository;
import pt.vclubroom.identity.api.UserDirectory;
import pt.vclubroom.membership.events.OwnerAdded;

/** The history module on its own (E14-S03): events in, timeline entries out. The club and identity APIs are mocked. */
@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
class HistoryModuleTests {

    @MockitoBean
    ClubCatalog clubs;

    @MockitoBean
    UserDirectory users;

    @Autowired
    TimelineEntryRepository entries;

    private List<TimelineEntry> timelineOf(UUID clubId) {
        return entries.findPage(clubId, Instant.parse("9999-12-31T00:00:00Z"), new UUID(-1L, -1L), Limit.of(10));
    }

    @Test
    void aRedeliveredEventAddsNothing(Scenario scenario) {
        UUID clubId = UUID.randomUUID();
        ClubApproved approved = new ClubApproved(clubId, "go", "Go Club", UUID.randomUUID(), UUID.randomUUID(), 1,
            Instant.now().truncatedTo(ChronoUnit.MICROS));

        scenario.publish(approved).andWaitForStateChange(() -> timelineOf(clubId), list -> list.size() == 1);
        scenario.publish(approved).andWaitForStateChange(() -> timelineOf(clubId), list -> list.size() == 1);   // at-least-once

        assertThat(timelineOf(clubId)).singleElement().satisfies(e -> {
            assertThat(e.type()).isEqualTo(TimelineEntryType.FOUNDED);
            assertThat(e.era()).isEqualTo(1);
        });
    }

    @Test
    void ownerEventsAreStampedWithTheClubsCurrentEra(Scenario scenario) {
        UUID clubId = UUID.randomUUID();
        given(clubs.require(clubId)).willReturn(new ClubSnapshot(clubId, "go", "Go Club", ClubStatus.ACTIVE, JoinPolicy.OPEN, 3));

        scenario.publish(new OwnerAdded(clubId, UUID.randomUUID(), "INVITATION", UUID.randomUUID(), Instant.now()))
            .andWaitForStateChange(() -> timelineOf(clubId), list -> !list.isEmpty())
            .andVerify(list -> assertThat(list).singleElement().satisfies(e -> {
                assertThat(e.era()).isEqualTo(3);
                assertThat(e.data()).containsEntry("reason", "INVITATION");
            }));
    }
}
