package pt.vclubroom.club.application;

import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import pt.vclubroom.club.api.JoinPolicy;
import pt.vclubroom.club.domain.Slug;
import pt.vclubroom.club.persistence.ClubRepository;
import pt.vclubroom.identity.api.UserDirectory;
import pt.vclubroom.shared.demo.DemoData;
import pt.vclubroom.shared.security.Actor;

/** Six clubs, requested by their founders and approved by alice; Chess Club is OPEN. */
@Component
@Profile(DemoData.PROFILE)
@Order(DemoData.CLUBS)
class ClubDemoSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ClubDemoSeeder.class);

    private record DemoClub(String name, String tagline, String category, List<String> tags, String description,
                            String founder, boolean open) { }

    private static final List<DemoClub> CLUBS = List.of(
        new DemoClub("Chess Club", "Blitz on Fridays", "Games", List.of("chess", "blitz"),
            "**Every level welcome.** Blitz on Fridays, puzzles on Sundays, a tournament each month.", "bob", true),
        new DemoClub("42 Robotics", "Robots, sensors and late nights", "Tech", List.of("robotics", "arduino"),
            "We build small robots and enter the national competition every spring.", "carol", false),
        new DemoClub("Photo Club", "Golden hour, every season", "Arts", List.of("photography"),
            "Photo walks around the city, a monthly theme, and critiques that help.", "dave", false),
        new DemoClub("Hiking Club", "Trails around Lisbon and Porto", "Sports", List.of("outdoors", "hiking"),
            "One trail a month, from easy to hard. Bring water and good shoes.", "bob", false),
        new DemoClub("Game Dev Club", "Ship a game every month", "Tech", List.of("gamedev", "unity"),
            "Small teams, one month, one playable game. Engines and languages are up to you.", "carol", false),
        new DemoClub("Film Society", "Cinema nights since the first promo", "Culture", List.of("cinema"),
            "A film every other Thursday, and a talk after it.", "dave", false));

    private final ClubService clubService;
    private final ClubRepository clubs;
    private final UserDirectory users;

    ClubDemoSeeder(ClubService clubService, ClubRepository clubs, UserDirectory users) {
        this.clubService = clubService;
        this.clubs = clubs;
        this.users = users;
    }

    @Override
    public void run(ApplicationArguments args) {
        Actor alice = new Actor(userId("alice"), true);
        for (DemoClub demo : CLUBS) {
            if (clubs.existsBySlug(Slug.fromName(demo.name()).value())) {
                continue;
            }
            UUID clubId = clubService.requestCreation(new Actor(userId(demo.founder()), false),
                new CreateClubCommand(demo.name(), demo.tagline(), demo.description(), demo.category(), demo.tags())).id();
            clubService.approve(alice, clubId);
            if (demo.open()) {                                             // club settings, as alice (R-08)
                clubService.updateProfile(alice, clubId, new UpdateClubCommand(null, null, null, null, null, null, null, JoinPolicy.OPEN));
            }
            log.info("Demo data: club {}", demo.name());
        }
    }

    private UUID userId(String login) {
        return users.findByLogin(login).orElseThrow().id();
    }
}
