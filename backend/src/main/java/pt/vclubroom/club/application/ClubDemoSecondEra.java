package pt.vclubroom.club.application;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import pt.vclubroom.club.api.ClubStatus;
import pt.vclubroom.club.domain.Club;
import pt.vclubroom.club.domain.RevivalStatus;
import pt.vclubroom.club.persistence.ClubRepository;
import pt.vclubroom.club.persistence.RevivalRequestRepository;
import pt.vclubroom.identity.api.UserDirectory;
import pt.vclubroom.shared.demo.DemoData;
import pt.vclubroom.shared.security.Actor;

/**
 * Film Society's first life ends: archived, then revived into era 2 the way students do it. Carol asks, alice
 * approves, and carol becomes the owner while dave, the founder, stays a member (R-05).
 */
@Component
@Profile(DemoData.PROFILE)
@Order(DemoData.SECOND_ERA)
class ClubDemoSecondEra implements ApplicationRunner {

    private final ClubService clubService;
    private final RevivalService revivals;
    private final ClubRepository clubs;
    private final RevivalRequestRepository requests;
    private final UserDirectory users;

    ClubDemoSecondEra(ClubService clubService, RevivalService revivals, ClubRepository clubs,
                      RevivalRequestRepository requests, UserDirectory users) {
        this.clubService = clubService;
        this.revivals = revivals;
        this.clubs = clubs;
        this.requests = requests;
        this.users = users;
    }

    @Override
    public void run(ApplicationArguments args) {
        Actor alice = new Actor(users.findByLogin("alice").orElseThrow().id(), true);
        Actor carol = new Actor(users.findByLogin("carol").orElseThrow().id(), false);
        Club film = clubs.findBySlug(DemoData.FILM_SOCIETY).orElseThrow();
        if (film.currentEra() != 1) {
            return;                                                        // seeded before
        }
        if (film.status() == ClubStatus.ACTIVE) {
            clubService.archive(alice, film.id(), "The founders graduated");
        }
        var pending = requests.findByClubIdAndStatus(film.id(), RevivalStatus.PENDING);
        var request = pending.isEmpty()
            ? revivals.request(carol, film.id(), "A new promo wants the projector back. I'll run the Thursday screenings.").id()
            : pending.getFirst().id();
        revivals.approve(alice, request);
    }
}
