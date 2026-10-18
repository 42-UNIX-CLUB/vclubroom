package pt.vclubroom.calendar.application;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import pt.vclubroom.calendar.domain.ClubEvent;
import pt.vclubroom.club.api.ClubCatalog;
import pt.vclubroom.club.api.ClubSnapshot;
import pt.vclubroom.media.api.MediaCatalog;
import pt.vclubroom.media.api.MediaView;
import pt.vclubroom.membership.api.ClubAuthorization;
import pt.vclubroom.membership.api.ClubPermission;
import pt.vclubroom.shared.security.Actor;

/** Builds EventDetails: club name, cover URL, RSVP counts and what the actor may do. */
@Component
class EventDetailsAssembler {

    private final ClubCatalog clubs;
    private final ClubAuthorization clubAuthorization;
    private final MediaCatalog media;
    private final RsvpService rsvps;
    private final Clock clock;

    EventDetailsAssembler(ClubCatalog clubs, ClubAuthorization clubAuthorization, MediaCatalog media,
                          RsvpService rsvps, Clock clock) {
        this.clubs = clubs;
        this.clubAuthorization = clubAuthorization;
        this.media = media;
        this.rsvps = rsvps;
        this.clock = clock;
    }

    EventDetails toDetails(Actor actor, ClubEvent e) {
        ClubSnapshot club = clubs.findById(e.clubId()).orElse(null);
        RsvpTally tally = rsvps.tally(Set.of(e.id()), actor.userId()).get(e.id());
        String coverUrl = null;
        if (e.coverMediaId() != null) {
            MediaView cover = media.resolve(List.of(e.coverMediaId())).get(e.coverMediaId());
            coverUrl = cover != null ? cover.url() : null;
        }
        return new EventDetails(e.id(), e.clubId(), club != null ? club.slug() : null, club != null ? club.name() : null,
            e.spaceId(), e.title(), e.description(), e.location(), e.onlineUrl(), e.startsAt(), e.endsAt(),
            e.status(), e.cancelReason(), coverUrl, e.createdBy(), tally.going(), tally.interested(), tally.myRsvp(),
            clubAuthorization.hasPermission(e.clubId(), actor, ClubPermission.EVENT_MANAGE),
            e.acceptsRsvp(clock.instant()));
    }
}
