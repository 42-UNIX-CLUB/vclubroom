package pt.vclubroom.calendar.web;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pt.vclubroom.calendar.application.CalendarItem;
import pt.vclubroom.calendar.application.CalendarQueryService;
import pt.vclubroom.calendar.application.CalendarQueryService.Scope;
import pt.vclubroom.shared.security.AuthenticatedUser;

/**
 * The campus and club calendars. from and to bind as OffsetDateTime because FullCalendar sends offsets
 * ("2026-10-01T00:00:00+01:00", 08-frontend §11.3).
 */
@RestController
class CalendarController {

    private final CalendarQueryService calendar;

    CalendarController(CalendarQueryService calendar) {
        this.calendar = calendar;
    }

    @GetMapping("/api/calendar")
    List<CalendarItem> campus(@AuthenticationPrincipal AuthenticatedUser me,
                              @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
                              @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
                              @RequestParam(name = "scope", defaultValue = "all") String scope) {
        return calendar.campus(me.actor(), from.toInstant(), to.toInstant(),
            "mine".equalsIgnoreCase(scope) ? Scope.MINE : Scope.ALL);
    }

    @GetMapping("/api/clubs/{clubId}/events")
    List<CalendarItem> club(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("clubId") UUID clubId,
                            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
                            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        return calendar.club(me.actor(), clubId, from.toInstant(), to.toInstant());
    }
}
