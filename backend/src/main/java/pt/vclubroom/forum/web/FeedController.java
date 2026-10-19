package pt.vclubroom.forum.web;

import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pt.vclubroom.forum.application.PostQueryService;
import pt.vclubroom.forum.web.dto.PostResponse;
import pt.vclubroom.shared.security.AuthenticatedUser;
import pt.vclubroom.shared.web.CursorPage;

/** The club feed (forum.md §7): the latest posts of every forum channel of the club the viewer can read. */
@RestController
class FeedController {

    private final PostQueryService queries;

    FeedController(PostQueryService queries) {
        this.queries = queries;
    }

    @GetMapping("/api/clubs/{clubId}/feed")
    CursorPage<PostResponse> feed(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("clubId") UUID clubId,
                                  @RequestParam(name = "era", required = false) Integer era,
                                  @RequestParam(name = "cursor", required = false) String cursor,
                                  @RequestParam(name = "limit", defaultValue = "20") int limit) {
        return queries.feed(me.actor(), clubId, era, cursor, Math.clamp(limit, 1, 50)).map(PostResponse::from);
    }
}
