package pt.vclubroom.calendar.web;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pt.vclubroom.calendar.application.CalendarItem;
import pt.vclubroom.calendar.application.CalendarQueryService;
import pt.vclubroom.shared.error.InvalidRequestException;
import pt.vclubroom.shared.security.AuthenticatedUser;

/** from/to are ISO-8601 with an offset (e.g. 2026-10-01T00:00Z); events overlapping [from, to) are returned. */
@RestController
class CalendarController {

    private final CalendarQueryService queries;

    CalendarController(CalendarQueryService queries) {
        this.queries = queries;
    }

    @GetMapping("/api/calendar")
    List<CalendarItem> campus(@AuthenticationPrincipal AuthenticatedUser me,
                              @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
                              @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
                              @RequestParam(name = "scope", defaultValue = "all") String scope,
                              @RequestParam(name = "clubIds", required = false) Set<UUID> clubIds) {
        boolean mineOnly = switch (scope) {
            case "all" -> false;
            case "mine" -> true;
            default -> throw new InvalidRequestException("INVALID_SCOPE", "scope must be 'all' or 'mine'.");
        };
        return queries.campus(me.actor(), from.toInstant(), to.toInstant(), mineOnly, clubIds);
    }

    @GetMapping("/api/clubs/{clubId}/events")
    List<CalendarItem> club(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("clubId") UUID clubId,
                            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
                            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        return queries.club(me.actor(), clubId, from.toInstant(), to.toInstant());
    }
}
