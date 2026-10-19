package pt.vclubroom.space.web;

import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pt.vclubroom.shared.security.AuthenticatedUser;
import pt.vclubroom.space.application.SpaceQueryService;
import pt.vclubroom.space.application.SpaceView;

/** Reading spaces. E05-S03 adds creating, editing and archiving them (SPACE_MANAGE). */
@RestController
class SpaceController {

    private final SpaceQueryService queries;

    SpaceController(SpaceQueryService queries) {
        this.queries = queries;
    }

    @GetMapping("/api/clubs/{clubId}/spaces")
    List<SpaceView> spaces(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("clubId") UUID clubId,
                           @RequestParam(name = "includeArchived", defaultValue = "false") boolean includeArchived) {
        return queries.spacesOf(clubId, me.actor(), includeArchived);
    }

    @GetMapping("/api/spaces/{spaceId}")
    SpaceView space(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("spaceId") UUID spaceId) {
        return queries.space(spaceId, me.actor());
    }
}
