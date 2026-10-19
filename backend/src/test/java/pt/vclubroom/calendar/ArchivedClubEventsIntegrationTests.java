package pt.vclubroom.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import pt.vclubroom.TestRequests;
import pt.vclubroom.TestcontainersConfiguration;
import pt.vclubroom.calendar.application.EventCommand;
import pt.vclubroom.calendar.application.EventService;
import pt.vclubroom.calendar.domain.EventStatus;
import pt.vclubroom.calendar.persistence.ClubEventRepository;
import pt.vclubroom.club.application.ClubService;
import pt.vclubroom.club.application.CreateClubCommand;
import pt.vclubroom.membership.api.ClubAuthorization;
import pt.vclubroom.shared.security.Actor;
import pt.vclubroom.space.persistence.SpaceRepository;

/** E11-S06: archiving a club cancels its future events (R-10); the past stays as it was. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ArchivedClubEventsIntegrationTests {

    @Autowired
    ClubService clubs;

    @Autowired
    ClubAuthorization authorization;

    @Autowired
    SpaceRepository spaces;

    @Autowired
    EventService events;

    @Autowired
    ClubEventRepository eventRepository;

    @Test
    void archivingCancelsWhatHasNotHappenedYet() {
        UUID founder = UUID.randomUUID();
        Actor owner = new Actor(founder, false);
        UUID clubId = clubs.requestCreation(owner,
            new CreateClubCommand("Robotics " + TestRequests.suffix(), null, null, "Tech", List.of())).id();
        clubs.approve(new Actor(UUID.randomUUID(), true), clubId);
        await().atMost(Duration.ofSeconds(10)).until(() ->
            authorization.isOwner(clubId, founder) && !spaces.findByClubIdOrderByPositionAsc(clubId).isEmpty());
        UUID general = spaces.findByClubIdOrderByPositionAsc(clubId).getFirst().id();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        UUID past = schedule(owner, clubId, general, "Kick-off", now.minus(Duration.ofDays(7)));
        UUID future = schedule(owner, clubId, general, "Competition", now.plus(Duration.ofDays(7)));
        UUID alreadyCancelled = schedule(owner, clubId, general, "Workshop", now.plus(Duration.ofDays(8)));
        events.cancel(owner, alreadyCancelled, "Mentor ill");

        clubs.archive(new Actor(UUID.randomUUID(), true), clubId, "Everyone graduated");

        await().atMost(Duration.ofSeconds(10)).until(() -> eventRepository.findById(future).orElseThrow().status() == EventStatus.CANCELLED);
        assertThat(eventRepository.findById(future).orElseThrow().cancelReason()).isEqualTo("The club was archived.");
        assertThat(eventRepository.findById(past).orElseThrow().status()).isEqualTo(EventStatus.SCHEDULED);       // history
        assertThat(eventRepository.findById(alreadyCancelled).orElseThrow().cancelReason()).isEqualTo("Mentor ill");
    }

    private UUID schedule(Actor owner, UUID clubId, UUID spaceId, String title, Instant start) {
        return events.schedule(owner, clubId,
            new EventCommand(spaceId, title, null, null, null, start, start.plus(Duration.ofHours(2)), null)).id();
    }
}
