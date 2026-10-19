package pt.vclubroom.club.application;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import pt.vclubroom.club.api.ClubStatus;
import pt.vclubroom.club.persistence.ClubRepository;
import pt.vclubroom.identity.api.UserDirectory;
import pt.vclubroom.shared.demo.DemoData;
import pt.vclubroom.shared.security.Actor;

/** Film Society ends its second era: an archived club with two eras of history to show. */
@Component
@Profile(DemoData.PROFILE)
@Order(DemoData.ARCHIVE)
class ClubDemoArchival implements ApplicationRunner {

    private final ClubService clubService;
    private final ClubRepository clubs;
    private final UserDirectory users;

    ClubDemoArchival(ClubService clubService, ClubRepository clubs, UserDirectory users) {
        this.clubService = clubService;
        this.clubs = clubs;
        this.users = users;
    }

    @Override
    public void run(ApplicationArguments args) {
        clubs.findBySlug(DemoData.FILM_SOCIETY)
            .filter(club -> club.status() == ClubStatus.ACTIVE && club.currentEra() == 2)
            .ifPresent(club -> clubService.archive(new Actor(users.findByLogin("alice").orElseThrow().id(), true),
                club.id(), "Taking a break until the next promo"));
    }
}
