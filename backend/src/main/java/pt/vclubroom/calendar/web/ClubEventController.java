package pt.vclubroom.calendar.web;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pt.vclubroom.calendar.application.EventService;
import pt.vclubroom.calendar.application.EventView;
import pt.vclubroom.calendar.web.dto.CancelEventRequest;
import pt.vclubroom.calendar.web.dto.EditEventRequest;
import pt.vclubroom.calendar.web.dto.ScheduleEventRequest;
import pt.vclubroom.shared.security.AuthenticatedUser;

/** One club event (calendar.md §7). E11-S02 adds the calendars (/api/calendar, /api/clubs/{clubId}/events). */
@RestController
class ClubEventController {

    private final EventService events;

    ClubEventController(EventService events) {
        this.events = events;
    }

    @PostMapping("/api/clubs/{clubId}/events")
    @ResponseStatus(HttpStatus.CREATED)
    EventView schedule(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("clubId") UUID clubId,
                       @Valid @RequestBody ScheduleEventRequest request) {
        return events.schedule(me.actor(), clubId, request.toCommand());
    }

    @GetMapping("/api/events/{eventId}")
    EventView get(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("eventId") UUID eventId) {
        return events.get(me.actor(), eventId);
    }

    @PatchMapping("/api/events/{eventId}")
    EventView edit(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("eventId") UUID eventId,
                   @Valid @RequestBody EditEventRequest request) {
        return events.edit(me.actor(), eventId, request.toCommand());
    }

    @PostMapping("/api/events/{eventId}/cancel")
    EventView cancel(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("eventId") UUID eventId,
                     @Valid @RequestBody CancelEventRequest request) {
        return events.cancel(me.actor(), eventId, request.reason());
    }
}
