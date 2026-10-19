package pt.vclubroom.calendar.application;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import pt.vclubroom.calendar.persistence.ClubEventRepository;
import pt.vclubroom.club.api.ClubCatalog;
import pt.vclubroom.identity.api.UserDirectory;
import pt.vclubroom.shared.demo.DemoData;
import pt.vclubroom.shared.security.Actor;
import pt.vclubroom.space.api.SpaceAccessPolicy;

/** Three events in the coming week, in the clubs' General spaces, scheduled by their owners (Lisbon time). */
@Component
@Profile(DemoData.PROFILE)
@Order(DemoData.EVENTS)
class CalendarDemoSeeder implements ApplicationRunner {

    private static final ZoneId LISBON = ZoneId.of("Europe/Lisbon");

    private record DemoEvent(String club, String owner, String title, String location, int inDays, LocalTime start, Duration length) { }

    private static final List<DemoEvent> EVENTS = List.of(
        new DemoEvent("chess-club", "bob", "Blitz night", "Cluster 2", 3, LocalTime.of(19, 0), Duration.ofHours(3)),
        new DemoEvent("photo-club", "dave", "Golden hour walk", "Miradouro de Santa Luzia", 4, LocalTime.of(17, 30), Duration.ofMinutes(90)),
        new DemoEvent("42-robotics", "carol", "Arduino workshop", "Lab 1", 6, LocalTime.of(18, 0), Duration.ofHours(2)));

    private final EventService events;
    private final ClubEventRepository eventRepository;
    private final SpaceAccessPolicy spaceAccess;
    private final ClubCatalog clubs;
    private final UserDirectory users;

    CalendarDemoSeeder(EventService events, ClubEventRepository eventRepository, SpaceAccessPolicy spaceAccess,
                       ClubCatalog clubs, UserDirectory users) {
        this.events = events;
        this.eventRepository = eventRepository;
        this.spaceAccess = spaceAccess;
        this.clubs = clubs;
        this.users = users;
    }

    @Override
    public void run(ApplicationArguments args) {
        Actor admin = new Actor(userId("alice"), true);
        for (DemoEvent demo : EVENTS) {
            UUID clubId = clubs.findBySlug(demo.club()).orElseThrow().id();
            if (eventRepository.existsByClubId(clubId)) {
                continue;
            }
            DemoData.awaitUntil(() -> !spaceAccess.readableForumChannels(clubId, admin).isEmpty(), demo.club() + "'s spaces");
            UUID general = spaceAccess.forChannel(spaceAccess.readableForumChannels(clubId, admin).iterator().next(), admin).spaceId();
            Instant start = LocalDate.now(LISBON).plusDays(demo.inDays()).atTime(demo.start()).atZone(LISBON).toInstant();
            events.schedule(new Actor(userId(demo.owner()), false), clubId,
                new EventCommand(general, demo.title(), null, demo.location(), null, start, start.plus(demo.length()), null));
        }
    }

    private UUID userId(String login) {
        return users.findByLogin(login).orElseThrow().id();
    }
}
