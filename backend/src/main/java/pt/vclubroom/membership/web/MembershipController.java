package pt.vclubroom.membership.web;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pt.vclubroom.membership.application.JoinOutcome;
import pt.vclubroom.membership.application.JoinService;
import pt.vclubroom.membership.application.MemberQueryService;
import pt.vclubroom.membership.application.MemberService;
import pt.vclubroom.membership.application.MembershipView;
import pt.vclubroom.membership.application.MyClubMembership;
import pt.vclubroom.membership.web.dto.JoinBody;
import pt.vclubroom.membership.web.dto.JoinResponse;
import pt.vclubroom.shared.security.AuthenticatedUser;

/** The current user's own membership: join, look, leave. /api/** requires a logged-in user (SecurityConfig). */
@RestController
class MembershipController {

    private final JoinService joins;
    private final MemberService members;
    private final MemberQueryService queries;

    MembershipController(JoinService joins, MemberService members, MemberQueryService queries) {
        this.joins = joins;
        this.members = members;
        this.queries = queries;
    }

    /** 201 JOINED (open club) or 202 REQUESTED (approval club). */
    @PostMapping("/api/clubs/{clubId}/join")
    ResponseEntity<JoinResponse> join(@AuthenticationPrincipal AuthenticatedUser me,
                                      @PathVariable("clubId") UUID clubId,
                                      @Valid @RequestBody(required = false) JoinBody body) {
        JoinOutcome outcome = joins.join(me.actor(), clubId, body == null ? null : body.message());
        HttpStatus status = outcome.status() == JoinOutcome.Status.JOINED ? HttpStatus.CREATED : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(JoinResponse.from(outcome));
    }

    /** A read model of IDs, strings and enums only, so it is the response as it is. */
    @GetMapping("/api/clubs/{clubId}/membership/me")
    MembershipView membership(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("clubId") UUID clubId) {
        return queries.membershipOf(clubId, me.actor());
    }

    @GetMapping("/api/me/memberships")
    List<MyClubMembership> myMemberships(@AuthenticationPrincipal AuthenticatedUser me) {
        return queries.myMemberships(me.actor());
    }

    @DeleteMapping("/api/clubs/{clubId}/members/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void leave(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("clubId") UUID clubId) {
        members.leave(me.actor(), clubId);
    }
}
