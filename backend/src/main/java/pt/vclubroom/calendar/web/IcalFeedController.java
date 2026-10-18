package pt.vclubroom.calendar.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import pt.vclubroom.calendar.application.FeedTokenService;
import pt.vclubroom.calendar.application.IcalFeedService;

/** /ical/** is permitAll in SecurityConfig: the secret token in the path is the credential (R-16). */
@RestController
class IcalFeedController {

    private final FeedTokenService tokens;
    private final IcalFeedService feeds;

    IcalFeedController(FeedTokenService tokens, IcalFeedService feeds) {
        this.tokens = tokens;
        this.feeds = feeds;
    }

    @GetMapping("/ical/feeds/{token}.ics")
    ResponseEntity<String> feed(@PathVariable String token) {
        return tokens.resolve(token)
            .map(userId -> ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/calendar; charset=utf-8"))
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=900")
                .body(feeds.feedFor(userId)))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
