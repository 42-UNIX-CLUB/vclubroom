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
import pt.vclubroom.club.application.RevivalService;
import pt.vclubroom.club.domain.RevivalStatus;
import pt.vclubroom.club.web.dto.ClubResponse;
import pt.vclubroom.club.web.dto.ReasonRequest;
import pt.vclubroom.club.web.dto.RevivalRequestResponse;
import pt.vclubroom.shared.security.AuthenticatedUser;
import pt.vclubroom.shared.web.PageResponse;

/** All routes under /api/admin/** additionally require ROLE_ADMIN in the security filter chain. */
@RestController
@RequestMapping("/api/admin")
class ClubAdminController {

    private final ClubService clubs;
    private final ClubQueryService queries;
    private final RevivalService revivals;

    ClubAdminController(ClubService clubs, ClubQueryService queries, RevivalService revivals) {
        this.clubs = clubs;
        this.queries = queries;
        this.revivals = revivals;
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

    @PostMapping("/clubs/{clubId}/archive")
    ClubResponse archive(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("clubId") UUID clubId,
                         @Valid @RequestBody ReasonRequest body) {
        return ClubResponse.from(clubs.archive(me.actor(), clubId, body.reason()));
    }

    @GetMapping("/revival-requests")
    PageResponse<RevivalRequestResponse> revivals(@AuthenticationPrincipal AuthenticatedUser me,
                                                  @RequestParam(name = "status", defaultValue = "PENDING") RevivalStatus status,
                                                  @RequestParam(name = "page", defaultValue = "0") int page,
                                                  @RequestParam(name = "size", defaultValue = "20") int size) {
        var result = revivals.byStatus(me.actor(), status, page, size);
        return new PageResponse<>(result.items().stream().map(RevivalRequestResponse::from).toList(),
            result.page(), result.size(), result.totalElements());
    }

    @PostMapping("/revival-requests/{requestId}/approve")
    ClubResponse approveRevival(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("requestId") UUID requestId) {
        return ClubResponse.from(revivals.approve(me.actor(), requestId));
    }

    @PostMapping("/revival-requests/{requestId}/reject")
    RevivalRequestResponse rejectRevival(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("requestId") UUID requestId,
                                         @Valid @RequestBody ReasonRequest body) {
        return RevivalRequestResponse.from(revivals.reject(me.actor(), requestId, body.reason()));
    }
}
