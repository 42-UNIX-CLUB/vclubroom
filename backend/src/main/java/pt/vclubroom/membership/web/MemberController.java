package pt.vclubroom.membership.web;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pt.vclubroom.membership.application.MemberQueryService;
import pt.vclubroom.membership.application.MemberService;
import pt.vclubroom.membership.web.dto.MemberResponse;
import pt.vclubroom.membership.web.dto.MemberSummaryResponse;
import pt.vclubroom.shared.security.AuthenticatedUser;
import pt.vclubroom.shared.web.PageResponse;

/** /api/** requires a logged-in user (SecurityConfig). */
@RestController
class MemberController {

    private final MemberQueryService queries;
    private final MemberService members;

    MemberController(MemberQueryService queries, MemberService members) {
        this.queries = queries;
        this.members = members;
    }

    /** Member count and owners for the club page; any logged-in user may see them. */
    @GetMapping("/api/clubs/{clubId}/member-summary")
    MemberSummaryResponse summary(@PathVariable("clubId") UUID clubId) {
        return MemberSummaryResponse.from(queries.summary(clubId));
    }

    /** "/members/me" (leave) is a literal path segment, so Spring prefers it to "{userId}" for DELETE. */
    @GetMapping("/api/clubs/{clubId}/members")
    PageResponse<MemberResponse> list(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("clubId") UUID clubId,
                                      @RequestParam(name = "query", required = false) String query,
                                      @RequestParam(name = "roleId", required = false) UUID roleId,
                                      @RequestParam(name = "page", defaultValue = "0") int page,
                                      @RequestParam(name = "size", defaultValue = "50") int size) {
        var result = queries.members(clubId, me.actor(), query, roleId, Math.max(page, 0), Math.clamp(size, 1, 100));
        return new PageResponse<>(result.items().stream().map(MemberResponse::from).toList(),
            result.page(), result.size(), result.totalElements());
    }

    @DeleteMapping("/api/clubs/{clubId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("clubId") UUID clubId,
                @PathVariable("userId") UUID userId) {
        members.remove(me.actor(), clubId, userId);
    }
}
