package pt.vclubroom.calendar.application;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.calendar.domain.ClubEvent;
import pt.vclubroom.calendar.persistence.ClubEventRepository;
import pt.vclubroom.club.api.ClubCatalog;
import pt.vclubroom.club.api.ClubSnapshot;
import pt.vclubroom.membership.api.ClubAuthorization;
import pt.vclubroom.shared.error.InvalidRequestException;
import pt.vclubroom.shared.security.Actor;
import pt.vclubroom.space.api.SpaceAccessPolicy;

/**
 * The calendars (calendar.md §8.2): one range query, then only the events of spaces the viewer can read (R-06),
 * decorated with their club and their RSVPs in batches.
 */
@Service
@Transactional(readOnly = true)
public class CalendarQueryService {

    static final Duration MAX_RANGE = Duration.ofDays(62);       // a month view with its spill-over weeks fits

    public enum Scope { ALL, MINE }

    private final ClubEventRepository events;
    private final ClubAuthorization clubAuthorization;
    private final SpaceAccessPolicy spaceAccess;
    private final ClubCatalog clubs;
    private final RsvpSummary rsvps;

    CalendarQueryService(ClubEventRepository events, ClubAuthorization clubAuthorization, SpaceAccessPolicy spaceAccess,
                         ClubCatalog clubs, RsvpSummary rsvps) {
        this.events = events;
        this.clubAuthorization = clubAuthorization;
        this.spaceAccess = spaceAccess;
        this.clubs = clubs;
        this.rsvps = rsvps;
    }

    /** Every event the viewer can read (ALL), or only those of their clubs (MINE). */
    public List<CalendarItem> campus(Actor actor, Instant from, Instant to, Scope scope) {
        requireRange(from, to);
        if (scope == Scope.ALL) {
            return readable(actor, events.findInRange(from, to));
        }
        Set<UUID> mine = clubAuthorization.clubIdsOf(actor.userId());
        return mine.isEmpty() ? List.of() : readable(actor, events.findInRangeForClubs(mine, from, to));
    }

    /** One club's events the viewer can read. */
    public List<CalendarItem> club(Actor actor, UUID clubId, Instant from, Instant to) {
        requireRange(from, to);
        return readable(actor, events.findInRangeForClubs(Set.of(clubId), from, to));
    }

    private List<CalendarItem> readable(Actor actor, List<ClubEvent> candidates) {
        Set<UUID> readableSpaces = spaceAccess.filterReadableSpaces(actor,
            candidates.stream().map(ClubEvent::spaceId).collect(Collectors.toSet()));
        List<ClubEvent> visible = candidates.stream().filter(e -> readableSpaces.contains(e.spaceId())).toList();
        Map<UUID, ClubSnapshot> clubById = clubs.findByIds(visible.stream().map(ClubEvent::clubId).distinct().toList());
        Map<UUID, RsvpSummary.Of> answers = rsvps.forEvents(visible.stream().map(ClubEvent::id).toList(), actor.userId());
        return visible.stream()
            .filter(e -> clubById.containsKey(e.clubId()))
            .map(e -> {
                ClubSnapshot club = clubById.get(e.clubId());
                RsvpSummary.Of a = answers.getOrDefault(e.id(), RsvpSummary.Of.NONE);
                return new CalendarItem(e.id(), e.title(), e.startsAt(), e.endsAt(), club.id(), club.slug(), club.name(),
                    e.status(), a.going(), a.interested(), a.mine());
            })
            .toList();
    }

    private static void requireRange(Instant from, Instant to) {
        if (!to.isAfter(from) || Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
            throw new InvalidRequestException("INVALID_RANGE", "Ask for at most 62 days, from before to.");
        }
    }
}
