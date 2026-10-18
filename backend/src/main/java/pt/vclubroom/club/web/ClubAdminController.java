package pt.vclubroom.club.web;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pt.vclubroom.club.api.ClubStatus;
import pt.vclubroom.club.application.ClubQueryService;
import pt.vclubroom.club.application.ClubService;
import pt.vclubroom.club.web.dto.ClubResponse;
import pt.vclubroom.club.web.dto.ReasonRequest;
import pt.vclubroom.shared.security.AuthenticatedUser;
import pt.vclubroom.shared.web.PageResponse;

/**
 * All routes under /api/admin/** additionally require ROLE_ADMIN in the security filter chain.
 * E14-S01 adds archive, E14-S02 the revival queue.
 */
@RestController
@RequestMapping("/api/admin")
class ClubAdminController {

    private final ClubService clubs;
    private final ClubQueryService queries;

    ClubAdminController(ClubService clubs, ClubQueryService queries) {
        this.clubs = clubs;
        this.queries = queries;
    }

    @GetMapping("/clubs")
    PageResponse<ClubResponse> list(@RequestParam(name = "status", defaultValue = "PENDING_APPROVAL") ClubStatus status,
                                    @RequestParam(name = "page", defaultValue = "0") int page,
                                    @RequestParam(name = "size", defaultValue = "20") int size) {
        var result = queries.byStatus(status, page, size);
        return new PageResponse<>(result.items().stream().map(ClubResponse::from).toList(),
            result.page(), result.size(), result.totalElements());
    }

    @PostMapping("/clubs/{clubId}/approve")
    ClubResponse approve(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("clubId") UUID clubId) {
        return ClubResponse.from(clubs.approve(me.actor(), clubId));
    }

    @PostMapping("/clubs/{clubId}/reject")
    ClubResponse reject(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("clubId") UUID clubId,
                        @Valid @RequestBody ReasonRequest body) {
        return ClubResponse.from(clubs.reject(me.actor(), clubId, body.reason()));
    }
}
