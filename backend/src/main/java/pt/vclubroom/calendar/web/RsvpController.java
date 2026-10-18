package pt.vclubroom.calendar.web;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pt.vclubroom.calendar.api.RsvpStatus;
import pt.vclubroom.calendar.application.RsvpService;
import pt.vclubroom.calendar.web.dto.RsvpRequest;
import pt.vclubroom.calendar.web.dto.RsvpResponse;
import pt.vclubroom.identity.api.UserSummary;
import pt.vclubroom.shared.security.AuthenticatedUser;

@RestController
class RsvpController {

    private final RsvpService rsvps;

    RsvpController(RsvpService rsvps) {
        this.rsvps = rsvps;
    }

    @PutMapping("/api/events/{eventId}/rsvp")
    RsvpResponse answer(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("eventId") UUID eventId,
                        @Valid @RequestBody RsvpRequest request) {
        return RsvpResponse.from(rsvps.answer(me.actor(), eventId, request.status()));
    }

    @DeleteMapping("/api/events/{eventId}/rsvp")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void withdraw(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("eventId") UUID eventId) {
        rsvps.withdraw(me.actor(), eventId);
    }

    @GetMapping("/api/events/{eventId}/attendees")
    List<UserSummary> attendees(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("eventId") UUID eventId,
                                @RequestParam(name = "status", defaultValue = "GOING") Set<RsvpStatus> status) {
        return rsvps.attendeeSummaries(me.actor(), eventId, status);
    }
}
