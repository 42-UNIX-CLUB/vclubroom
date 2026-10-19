package pt.vclubroom.membership.web;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pt.vclubroom.membership.application.JoinRequestView;
import pt.vclubroom.membership.application.JoinService;
import pt.vclubroom.membership.application.MemberQueryService;
import pt.vclubroom.membership.domain.JoinRequestStatus;
import pt.vclubroom.shared.security.AuthenticatedUser;

/** The queue of an APPROVAL club (MEMBER_APPROVE), and the requester's own cancel. */
@RestController
@RequestMapping("/api/clubs/{clubId}/join-requests")
class JoinRequestController {

    private final JoinService joins;
    private final MemberQueryService queries;

    JoinRequestController(JoinService joins, MemberQueryService queries) {
        this.joins = joins;
        this.queries = queries;
    }

    @GetMapping
    List<JoinRequestView> list(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("clubId") UUID clubId,
                               @RequestParam(name = "status", defaultValue = "PENDING") JoinRequestStatus status) {
        return queries.joinRequests(clubId, status, me.actor());
    }

    @PostMapping("/{requestId}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void approve(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("clubId") UUID clubId,
                 @PathVariable("requestId") UUID requestId) {
        joins.approve(me.actor(), clubId, requestId);
    }

    @PostMapping("/{requestId}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reject(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("clubId") UUID clubId,
                @PathVariable("requestId") UUID requestId) {
        joins.reject(me.actor(), clubId, requestId);
    }

    @DeleteMapping("/mine")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancelMine(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("clubId") UUID clubId) {
        joins.cancelMine(me.actor(), clubId);
    }
}
