package pt.vclubroom.calendar.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pt.vclubroom.calendar.application.FeedTokenService;
import pt.vclubroom.calendar.web.dto.CalendarFeedResponse;
import pt.vclubroom.shared.security.AuthenticatedUser;

/** My calendar feed (calendar.md §7): whether I have one, a new secret URL (shown once), or none. */
@RestController
@RequestMapping("/api/me/calendar-feed")
class FeedTokenController {

    private final FeedTokenService tokens;
    private final String publicUrl;

    FeedTokenController(FeedTokenService tokens, @Value("${app.public-url}") String publicUrl) {
        this.tokens = tokens;
        this.publicUrl = publicUrl.replaceAll("/+$", "");
    }

    @GetMapping
    CalendarFeedResponse mine(@AuthenticationPrincipal AuthenticatedUser me) {
        return new CalendarFeedResponse(null, tokens.createdAt(me.userId()).orElse(null));
    }

    /** Creates or replaces it: the old URL stops working. */
    @PostMapping
    CalendarFeedResponse rotate(@AuthenticationPrincipal AuthenticatedUser me) {
        String token = tokens.rotate(me.userId());
        return new CalendarFeedResponse(publicUrl + "/ical/feeds/" + token + ".ics", tokens.createdAt(me.userId()).orElse(null));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(@AuthenticationPrincipal AuthenticatedUser me) {
        tokens.revoke(me.userId());
    }
}
