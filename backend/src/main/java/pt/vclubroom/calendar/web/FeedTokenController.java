package pt.vclubroom.calendar.web;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pt.vclubroom.calendar.application.FeedTokenService;
import pt.vclubroom.calendar.web.dto.FeedUrlResponse;
import pt.vclubroom.shared.security.AuthenticatedUser;

/** POST creates or rotates the personal feed (the previous URL stops working); DELETE revokes it. */
@RestController
class FeedTokenController {

    private final FeedTokenService tokens;

    FeedTokenController(FeedTokenService tokens) {
        this.tokens = tokens;
    }

    @PostMapping("/api/me/calendar-feed")
    FeedUrlResponse rotate(@AuthenticationPrincipal AuthenticatedUser me) {
        return new FeedUrlResponse(tokens.rotate(me.userId()));
    }

    @DeleteMapping("/api/me/calendar-feed")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(@AuthenticationPrincipal AuthenticatedUser me) {
        tokens.revoke(me.userId());
    }
}
