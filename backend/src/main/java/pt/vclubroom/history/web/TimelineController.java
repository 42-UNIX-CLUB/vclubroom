package pt.vclubroom.history.web;

import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pt.vclubroom.history.application.TimelineEntryView;
import pt.vclubroom.history.application.TimelineQueryService;
import pt.vclubroom.shared.security.AuthenticatedUser;
import pt.vclubroom.shared.web.CursorPage;

@RestController
class TimelineController {

    private final TimelineQueryService timeline;

    TimelineController(TimelineQueryService timeline) {
        this.timeline = timeline;
    }

    @GetMapping("/api/clubs/{clubId}/timeline")
    CursorPage<TimelineEntryView> timeline(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("clubId") UUID clubId,
                                           @RequestParam(name = "cursor", required = false) String cursor,
                                           @RequestParam(name = "limit", defaultValue = "30") int limit) {
        return timeline.page(me.actor(), clubId, cursor, Math.clamp(limit, 1, 100));
    }
}
