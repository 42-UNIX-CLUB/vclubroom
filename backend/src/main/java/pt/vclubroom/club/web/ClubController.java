package pt.vclubroom.club.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import pt.vclubroom.club.application.ClubQueryService;
import pt.vclubroom.club.application.ClubService;
import pt.vclubroom.club.web.dto.ClubResponse;
import pt.vclubroom.club.web.dto.CreateClubRequest;
import pt.vclubroom.shared.security.AuthenticatedUser;

/** /api/** requires a logged-in user (SecurityConfig). E04-S04 adds PATCH /api/clubs/{clubId}, E14-S03 the eras. */
@RestController
class ClubController {

    private final ClubService clubs;
    private final ClubQueryService queries;

    ClubController(ClubService clubs, ClubQueryService queries) {
        this.clubs = clubs;
        this.queries = queries;
    }

    @PostMapping("/api/clubs")
    @ResponseStatus(HttpStatus.CREATED)
    ClubResponse requestCreation(@AuthenticationPrincipal AuthenticatedUser me,
                                 @Valid @RequestBody CreateClubRequest request) {
        return ClubResponse.from(clubs.requestCreation(me.actor(), request.toCommand()));
    }

    @GetMapping("/api/clubs/{idOrSlug}")
    ClubResponse get(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("idOrSlug") String idOrSlug) {
        return ClubResponse.from(queries.get(me.actor(), idOrSlug));
    }

    @GetMapping("/api/me/club-requests")
    List<ClubResponse> myRequests(@AuthenticationPrincipal AuthenticatedUser me) {
        return queries.myRequests(me.actor()).stream().map(ClubResponse::from).toList();
    }
}