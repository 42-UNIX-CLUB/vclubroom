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
import pt.vclubroom.calendar.application.CalendarQueryService;
import pt.vclubroom.calendar.application.EventService;
import pt.vclubroom.calendar.web.dto.CancelEventRequest;
import pt.vclubroom.calendar.web.dto.CreateEventRequest;
import pt.vclubroom.calendar.web.dto.EditEventRequest;
import pt.vclubroom.calendar.web.dto.EventResponse;
import pt.vclubroom.shared.security.AuthenticatedUser;

/** Writes require EVENT_MANAGE in the event's club; reads require read access to its space (calendar.md §7). */
@RestController
class ClubEventController {

    private final EventService events;
    private final CalendarQueryService queries;

    ClubEventController(EventService events, CalendarQueryService queries) {
        this.events = events;
        this.queries = queries;
    }

    @PostMapping("/api/clubs/{clubId}/events")
    @ResponseStatus(HttpStatus.CREATED)
    EventResponse schedule(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("clubId") UUID clubId,
                           @Valid @RequestBody CreateEventRequest request) {
        return EventResponse.from(events.schedule(me.actor(), clubId, request.toCommand()));
    }

    @GetMapping("/api/events/{eventId}")
    EventResponse get(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("eventId") UUID eventId) {
        return EventResponse.from(queries.event(me.actor(), eventId));
    }

    @PatchMapping("/api/events/{eventId}")
    EventResponse edit(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("eventId") UUID eventId,
                       @Valid @RequestBody EditEventRequest request) {
        return EventResponse.from(events.edit(me.actor(), eventId, request.toCommand()));
    }

    @PostMapping("/api/events/{eventId}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancel(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("eventId") UUID eventId,
                @Valid @RequestBody CancelEventRequest request) {
        events.cancel(me.actor(), eventId, request.reason());
    }
}
