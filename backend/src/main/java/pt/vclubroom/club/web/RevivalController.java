package pt.vclubroom.club.web;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pt.vclubroom.club.application.RevivalService;
import pt.vclubroom.club.web.dto.RevivalRequestBody;
import pt.vclubroom.club.web.dto.RevivalRequestResponse;
import pt.vclubroom.shared.security.AuthenticatedUser;

/** A student asks to revive an archived club. Admins decide in ClubAdminController. */
@RestController
class RevivalController {

    private final RevivalService revivals;

    RevivalController(RevivalService revivals) {
        this.revivals = revivals;
    }

    @PostMapping("/api/clubs/{clubId}/revival-requests")
    @ResponseStatus(HttpStatus.CREATED)
    RevivalRequestResponse request(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("clubId") UUID clubId,
                                   @Valid @RequestBody RevivalRequestBody body) {
        return RevivalRequestResponse.from(revivals.request(me.actor(), clubId, body.motivation()));
    }
}
