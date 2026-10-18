package pt.vclubroom.calendar.application;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
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

/** Read side: campus and club calendars and the event page. Events of unreadable spaces are dropped (R-06). */
@Service
@Transactional(readOnly = true)
public class CalendarQueryService {

    private final ClubEventRepository events;
    private final EventAccess access;
    private final EventDetailsAssembler assembler;
    private final ClubAuthorization clubAuthorization;
    private final SpaceAccessPolicy spaceAccess;
    private final ClubCatalog clubs;
    private final RsvpService rsvps;
    private final CalendarProperties properties;

    public CalendarQueryService(ClubEventRepository events, EventAccess access, EventDetailsAssembler assembler,
                                ClubAuthorization clubAuthorization, SpaceAccessPolicy spaceAccess,
                                ClubCatalog clubs, RsvpService rsvps, CalendarProperties properties) {
        this.events = events;
        this.access = access;
        this.assembler = assembler;
        this.clubAuthorization = clubAuthorization;
        this.spaceAccess = spaceAccess;
        this.clubs = clubs;
        this.rsvps = rsvps;
        this.properties = properties;
    }

    /** Campus calendar (calendar.md §8.2). mineOnly keeps the actor's clubs; clubIds, when given, narrows further. */
    public List<CalendarItem> campus(Actor actor, Instant from, Instant to, boolean mineOnly, Set<UUID> clubIds) {
        requireValidRange(from, to);
        Set<UUID> clubFilter = null;
        if (mineOnly) {
            clubFilter = new HashSet<>(clubAuthorization.clubIdsOf(actor.userId()));
            if (clubIds != null && !clubIds.isEmpty()) {
                clubFilter.retainAll(clubIds);
            }
        } else if (clubIds != null && !clubIds.isEmpty()) {
            clubFilter = clubIds;
        }
        List<ClubEvent> found;
        if (clubFilter == null) {
            found = events.findInRange(from, to);
        } else {
            found = clubFilter.isEmpty() ? List.of() : events.findInRangeForClubs(clubFilter, from, to);
        }
        return toItems(actor, found);
    }

    public List<CalendarItem> club(Actor actor, UUID clubId, Instant from, Instant to) {
        requireValidRange(from, to);
        return toItems(actor, events.findInRangeForClubs(Set.of(clubId), from, to));
    }

    public EventDetails event(Actor actor, UUID eventId) {
        return assembler.toDetails(actor, access.requireReadable(eventId, actor));
    }

    private List<CalendarItem> toItems(Actor actor, List<ClubEvent> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        Set<UUID> readable = spaceAccess.filterReadableSpaces(actor,
            candidates.stream().map(ClubEvent::spaceId).collect(Collectors.toSet()));
        List<ClubEvent> visible = candidates.stream().filter(e -> readable.contains(e.spaceId())).toList();
        if (visible.isEmpty()) {
            return List.of();
        }
        Map<UUID, ClubSnapshot> clubById = clubs.findByIds(
            visible.stream().map(ClubEvent::clubId).collect(Collectors.toSet()));
        Map<UUID, RsvpTally> tallies = rsvps.tally(
            visible.stream().map(ClubEvent::id).collect(Collectors.toSet()), actor.userId());
        return visible.stream().map(e -> {
            ClubSnapshot club = clubById.get(e.clubId());
            RsvpTally tally = tallies.get(e.id());
            return new CalendarItem(e.id(), e.title(), e.startsAt(), e.endsAt(), e.clubId(),
                club != null ? club.slug() : null, club != null ? club.name() : null, e.status(),
                tally.going(), tally.interested(), tally.myRsvp());
        }).toList();
    }

    private void requireValidRange(Instant from, Instant to) {
        Duration max = Duration.ofDays(properties.maxRangeDays());
        if (!to.isAfter(from) || Duration.between(from, to).compareTo(max) > 0) {
            throw new InvalidRequestException("INVALID_RANGE",
                "The range must end after it starts and span at most " + properties.maxRangeDays() + " days.");
        }
    }
}
