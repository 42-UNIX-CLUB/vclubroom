package pt.vclubroom.membership.web;

import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import pt.vclubroom.membership.application.MemberQueryService;
import pt.vclubroom.membership.web.dto.MemberSummaryResponse;

/** /api/** requires a logged-in user (SecurityConfig). E07-S04 adds the member list and member removal. */
@RestController
class MemberController {

    private final MemberQueryService queries;

    MemberController(MemberQueryService queries) {
        this.queries = queries;
    }

    /** Member count and owners for the club page; any logged-in user may see them. */
    @GetMapping("/api/clubs/{clubId}/member-summary")
    MemberSummaryResponse summary(@PathVariable("clubId") UUID clubId) {
        return MemberSummaryResponse.from(queries.summary(clubId));
    }
}
