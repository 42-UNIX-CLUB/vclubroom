package pt.vclubroom.calendar.web;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pt.vclubroom.calendar.api.RsvpStatus;
import pt.vclubroom.calendar.application.AttendeeView;
import pt.vclubroom.calendar.application.EventView;
import pt.vclubroom.calendar.application.RsvpService;
import pt.vclubroom.calendar.web.dto.RsvpRequest;
import pt.vclubroom.shared.security.AuthenticatedUser;

/** My answer to an event, and who is coming (calendar.md §7). Both answers return the event with its new counts. */
@RestController
class RsvpController {

    private final RsvpService rsvps;

    RsvpController(RsvpService rsvps) {
        this.rsvps = rsvps;
    }

    @PutMapping("/api/events/{eventId}/rsvp")
    EventView answer(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("eventId") UUID eventId,
                     @Valid @RequestBody RsvpRequest request) {
        return rsvps.answer(me.actor(), eventId, request.status());
    }

    @DeleteMapping("/api/events/{eventId}/rsvp")
    EventView withdraw(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("eventId") UUID eventId) {
        return rsvps.withdraw(me.actor(), eventId);
    }

    @GetMapping("/api/events/{eventId}/attendees")
    List<AttendeeView> attendees(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("eventId") UUID eventId,
                                 @RequestParam(name = "status", defaultValue = "GOING,INTERESTED") Set<RsvpStatus> statuses) {
        return rsvps.attendees(me.actor(), eventId, statuses);
    }
}
