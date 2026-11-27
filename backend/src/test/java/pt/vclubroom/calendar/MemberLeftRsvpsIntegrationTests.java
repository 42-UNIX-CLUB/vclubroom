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
import org.springframework.transaction.support.TransactionTemplate;
import pt.vclubroom.TestRequests;
import pt.vclubroom.TestcontainersConfiguration;
import pt.vclubroom.calendar.api.RsvpStatus;
import pt.vclubroom.calendar.application.EventCommand;
import pt.vclubroom.calendar.application.EventService;
import pt.vclubroom.calendar.application.RsvpService;
import pt.vclubroom.calendar.domain.Rsvp;
import pt.vclubroom.calendar.persistence.RsvpRepository;
import pt.vclubroom.membership.api.ClubAuthorization;
import pt.vclubroom.club.application.ClubService;
import pt.vclubroom.club.application.CreateClubCommand;
import pt.vclubroom.club.api.JoinPolicy;
import pt.vclubroom.club.persistence.ClubRepository;
import pt.vclubroom.identity.api.PlatformRole;
import pt.vclubroom.identity.domain.User;
import pt.vclubroom.identity.persistence.UserRepository;
import pt.vclubroom.membership.application.JoinService;
import pt.vclubroom.membership.application.MemberService;
import pt.vclubroom.shared.security.Actor;
import pt.vclubroom.space.persistence.SpaceRepository;

/** Leaving a club, or being removed, takes back one's answers to its upcoming events; past ones stay as history. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MemberLeftRsvpsIntegrationTests {

    @Autowired ClubService clubs;
    @Autowired ClubAuthorization authorization;
    @Autowired ClubRepository clubRepository;
    @Autowired SpaceRepository spaces;
    @Autowired EventService events;
    @Autowired RsvpService rsvps;
    @Autowired RsvpRepository rsvpRepository;
    @Autowired JoinService joins;
    @Autowired MemberService members;
    @Autowired UserRepository users;
    @Autowired TransactionTemplate tx;

    @Test
    void aRemovedMembersUpcomingAnswersGoAndTheirPastOnesStay() {
        UUID founder = person("Founder");
        UUID ada = person("Ada");
        Actor owner = new Actor(founder, false);
        UUID clubId = clubs.requestCreation(owner,
            new CreateClubCommand("Hiking " + TestRequests.suffix(), null, null, "Sports", List.of())).id();
        clubs.approve(new Actor(UUID.randomUUID(), true), clubId);
        await().atMost(Duration.ofSeconds(10)).until(() ->
            authorization.isOwner(clubId, founder) && !spaces.findByClubIdOrderByPositionAsc(clubId).isEmpty());
        tx.executeWithoutResult(s -> clubRepository.findById(clubId).orElseThrow().changeJoinPolicy(JoinPolicy.OPEN, Instant.now()));
        joins.join(new Actor(ada, false), clubId, null);
        UUID general = spaces.findByClubIdOrderByPositionAsc(clubId).getFirst().id();
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        UUID past = schedule(owner, clubId, general, "Last hike", now.minus(Duration.ofDays(7)));
        UUID upcoming = schedule(owner, clubId, general, "Next hike", now.plus(Duration.ofDays(7)));
        rsvps.answer(new Actor(ada, false), upcoming, RsvpStatus.GOING);
        rsvpRepository.save(Rsvp.answer(past, ada, RsvpStatus.GOING, now.minus(Duration.ofDays(8))));   // answered back then

        members.remove(owner, clubId, ada);

        await().atMost(Duration.ofSeconds(10)).until(() -> rsvpRepository.findByEventIdAndUserId(upcoming, ada).isEmpty());
        assertThat(rsvpRepository.findByEventIdAndUserId(past, ada)).isPresent();                          // history
        assertThat(rsvps.attendees(upcoming, java.util.Set.of(RsvpStatus.GOING))).doesNotContain(ada);    // no reminders
    }

    private UUID person(String name) {
        String login = name.toLowerCase() + "-" + TestRequests.suffix();
        return users.save(User.createLocal(login, name, login + "@example.test", "{noop}x", PlatformRole.USER, Instant.now())).id();
    }

    private UUID schedule(Actor owner, UUID clubId, UUID spaceId, String title, Instant start) {
        return events.schedule(owner, clubId,
            new EventCommand(spaceId, title, null, null, null, start, start.plus(Duration.ofHours(2)), null)).id();
    }
}
