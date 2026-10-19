package pt.vclubroom.history.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.club.api.ClubCatalog;
import pt.vclubroom.club.api.ClubSnapshot;
import pt.vclubroom.club.api.ClubStatus;
import pt.vclubroom.history.domain.TimelineEntry;
import pt.vclubroom.history.persistence.TimelineEntryRepository;
import pt.vclubroom.identity.api.UserDirectory;
import pt.vclubroom.identity.api.UserSummary;
import pt.vclubroom.shared.error.NotFoundException;
import pt.vclubroom.shared.security.Actor;
import pt.vclubroom.shared.web.CursorPage;
import pt.vclubroom.shared.web.TimeCursor;

/** The timeline of an open club, active or archived, for any logged-in user; pending or rejected clubs: admins only. */
@Service
@Transactional(readOnly = true)
public class TimelineQueryService {

    /** "Newer than any entry": the first page starts here. */
    private static final TimeCursor FIRST_PAGE = new TimeCursor(Instant.parse("9999-12-31T23:59:59Z"), new UUID(-1L, -1L));

    private final TimelineEntryRepository entries;
    private final ClubCatalog clubs;
    private final UserDirectory users;

    TimelineQueryService(TimelineEntryRepository entries, ClubCatalog clubs, UserDirectory users) {
        this.entries = entries;
        this.clubs = clubs;
        this.users = users;
    }

    public CursorPage<TimelineEntryView> page(Actor actor, UUID clubId, String cursor, int limit) {
        ClubSnapshot club = clubs.require(clubId);
        boolean open = club.status() == ClubStatus.ACTIVE || club.status() == ClubStatus.ARCHIVED;
        if (!open && !actor.admin()) {
            throw new NotFoundException("CLUB_NOT_FOUND", "Club not found");
        }
        TimeCursor after = cursor == null ? FIRST_PAGE : TimeCursor.decode(cursor);
        List<TimelineEntry> rows = entries.findPage(clubId, after.createdAt(), after.id(), Limit.of(limit + 1));
        List<TimelineEntry> page = rows.size() > limit ? rows.subList(0, limit) : rows;
        String next = rows.size() > limit ? new TimeCursor(page.getLast().occurredAt(), page.getLast().id()).encode() : null;

        Map<UUID, UserSummary> people = users.findByIds(page.stream()
            .flatMap(e -> Stream.of(e.actorId(), e.subjectId())).filter(Objects::nonNull).distinct().toList());
        return new CursorPage<>(page.stream()
            .map(e -> new TimelineEntryView(e.id(), e.era(), e.type(),
                e.actorId() == null ? null : people.get(e.actorId()),
                e.subjectId() == null ? null : people.get(e.subjectId()), e.data(), e.occurredAt()))
            .toList(), next);
    }
}
