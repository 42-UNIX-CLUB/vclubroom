package pt.vclubroom.calendar.application;

import biweekly.Biweekly;
import biweekly.ICalendar;
import biweekly.component.VEvent;
import biweekly.property.Status;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.calendar.domain.ClubEvent;
import pt.vclubroom.calendar.domain.EventStatus;
import pt.vclubroom.calendar.persistence.ClubEventRepository;
import pt.vclubroom.club.api.ClubCatalog;
import pt.vclubroom.club.api.ClubSnapshot;
import pt.vclubroom.membership.api.ClubAuthorization;
import pt.vclubroom.shared.security.Actor;
import pt.vclubroom.space.api.SpaceAccessPolicy;

/** Builds the personal .ics feed. The caller has already resolved the token to a user. */
@Service
@Transactional(readOnly = true)
public class IcalFeedService {

    private final ClubEventRepository events;
    private final ClubAuthorization clubAuthorization;
    private final SpaceAccessPolicy spaceAccess;
    private final ClubCatalog clubs;
    private final Clock clock;
    private final String publicUrl;

    public IcalFeedService(ClubEventRepository events, ClubAuthorization clubAuthorization,
                           SpaceAccessPolicy spaceAccess, ClubCatalog clubs, Clock clock,
                           @Value("${app.public-url}") String publicUrl) {
        this.events = events;
        this.clubAuthorization = clubAuthorization;
        this.spaceAccess = spaceAccess;
        this.clubs = clubs;
        this.clock = clock;
        this.publicUrl = publicUrl;
    }

    /** Evaluated as a regular user (never with admin bypass): the feed shows what the user sees as a member. */
    public String feedFor(UUID userId) {
        Actor actor = new Actor(userId, false);
        Instant now = clock.instant();
        Set<UUID> clubIds = clubAuthorization.clubIdsOf(userId);
        List<ClubEvent> candidates = clubIds.isEmpty() ? List.of()
            : events.findInRangeForClubs(clubIds, now.minus(Duration.ofDays(30)), now.plus(Duration.ofDays(180)));
        Set<UUID> readable = spaceAccess.filterReadableSpaces(actor,
            candidates.stream().map(ClubEvent::spaceId).collect(Collectors.toSet()));
        Map<UUID, ClubSnapshot> clubById = clubs.findByIds(clubIds);

        ICalendar ical = new ICalendar();
        ical.setProductId("-//Virtual Clubroom//vclubroom//EN");
        for (ClubEvent e : candidates) {
            if (!readable.contains(e.spaceId())) {
                continue;
            }
            ClubSnapshot club = clubById.get(e.clubId());
            VEvent v = new VEvent();
            v.setUid(e.id() + "@vclubroom");
            v.setSummary((club != null ? "[" + club.name() + "] " : "") + e.title());
            v.setDescription(e.description());
            v.setLocation(e.location() != null ? e.location() : e.onlineUrl());
            v.setDateStart(Date.from(e.startsAt()));
            v.setDateEnd(Date.from(e.endsAt()));
            if (club != null) {
                v.setUrl(publicUrl + "/c/" + club.slug() + "/events/" + e.id());
            }
            if (e.status() == EventStatus.CANCELLED) {
                v.setStatus(Status.cancelled());
            }
            ical.addEvent(v);
        }
        return Biweekly.write(ical).go();
    }
}
