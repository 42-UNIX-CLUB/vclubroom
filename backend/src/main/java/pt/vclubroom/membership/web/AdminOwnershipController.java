package pt.vclubroom.membership.web;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pt.vclubroom.membership.application.OwnershipService;
import pt.vclubroom.membership.web.dto.AssignOwnerRequest;
import pt.vclubroom.shared.security.AuthenticatedUser;

/** D-22: admins rescue a club's ownership. /api/admin/** requires ROLE_ADMIN (SecurityConfig); the service checks again. */
@RestController
@RequestMapping("/api/admin/clubs/{clubId}/owners")
class AdminOwnershipController {

    private final OwnershipService ownership;

    AdminOwnershipController(OwnershipService ownership) {
        this.ownership = ownership;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void assign(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("clubId") UUID clubId,
                @Valid @RequestBody AssignOwnerRequest request) {
        ownership.assignOwner(me.actor(), clubId, request.userId());
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("clubId") UUID clubId,
                @PathVariable("userId") UUID userId) {
        ownership.removeOwner(me.actor(), clubId, userId);
    }
}
