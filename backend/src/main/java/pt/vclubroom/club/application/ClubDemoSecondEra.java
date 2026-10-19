package pt.vclubroom.club.application;

import java.time.Clock;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import pt.vclubroom.club.api.ClubStatus;
import pt.vclubroom.club.persistence.ClubRepository;
import pt.vclubroom.identity.api.UserDirectory;
import pt.vclubroom.shared.demo.DemoData;
import pt.vclubroom.shared.security.Actor;

/**
 * Film Society's first life ends: archived, then revived into era 2. The revival is done on the club itself until
 * E14-S02 brings revival requests (and their ClubRevived event).
 */
@Component
@Profile(DemoData.PROFILE)
@Order(DemoData.SECOND_ERA)
class ClubDemoSecondEra implements ApplicationRunner {

    private final ClubService clubService;
    private final ClubRepository clubs;
    private final UserDirectory users;
    private final TransactionTemplate tx;
    private final Clock clock;

    ClubDemoSecondEra(ClubService clubService, ClubRepository clubs, UserDirectory users, TransactionTemplate tx, Clock clock) {
        this.clubService = clubService;
        this.clubs = clubs;
        this.users = users;
        this.tx = tx;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        clubs.findBySlug(DemoData.FILM_SOCIETY)
            .filter(club -> club.status() == ClubStatus.ACTIVE && club.currentEra() == 1)
            .ifPresent(club -> {
                clubService.archive(new Actor(users.findByLogin("alice").orElseThrow().id(), true), club.id(),
                    "The founders graduated");
                tx.executeWithoutResult(status -> clubs.findWithErasById(club.id()).orElseThrow().revive(clock.instant()));
            });
    }
}
