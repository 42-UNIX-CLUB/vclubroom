package pt.vclubroom.membership.web;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pt.vclubroom.membership.application.InvitationView;
import pt.vclubroom.membership.application.MemberQueryService;
import pt.vclubroom.membership.application.OwnershipService;
import pt.vclubroom.membership.web.dto.InvitationCreated;
import pt.vclubroom.membership.web.dto.InviteOwnerRequest;
import pt.vclubroom.shared.security.AuthenticatedUser;

/** Co-ownership: invitations (owners send, invitees answer) and stepping down. */
@RestController
class OwnershipController {

    private final OwnershipService ownership;
    private final MemberQueryService queries;

    OwnershipController(OwnershipService ownership, MemberQueryService queries) {
        this.ownership = ownership;
        this.queries = queries;
    }

    @PostMapping("/api/clubs/{clubId}/ownership-invitations")
    @ResponseStatus(HttpStatus.CREATED)
    InvitationCreated invite(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("clubId") UUID clubId,
                             @Valid @RequestBody InviteOwnerRequest request) {
        return new InvitationCreated(ownership.invite(me.actor(), clubId, request.inviteeId(), request.transfer()));
    }

    /** Not in membership.md §7: the Ownership tab lists what is waiting, so owners can revoke it. */
    @GetMapping("/api/clubs/{clubId}/ownership-invitations")
    List<InvitationView> clubInvitations(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("clubId") UUID clubId) {
        return queries.clubInvitations(clubId, me.actor());
    }

    @GetMapping("/api/me/ownership-invitations")
    List<InvitationView> myInvitations(@AuthenticationPrincipal AuthenticatedUser me) {
        return queries.myInvitations(me.actor());
    }

    @PostMapping("/api/ownership-invitations/{invitationId}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void accept(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("invitationId") UUID invitationId) {
        ownership.accept(me.actor(), invitationId);
    }

    @PostMapping("/api/ownership-invitations/{invitationId}/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void decline(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("invitationId") UUID invitationId) {
        ownership.decline(me.actor(), invitationId);
    }

    @DeleteMapping("/api/ownership-invitations/{invitationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("invitationId") UUID invitationId) {
        ownership.revoke(me.actor(), invitationId);
    }

    @PostMapping("/api/clubs/{clubId}/owners/me/step-down")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void stepDown(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("clubId") UUID clubId) {
        ownership.stepDown(me.actor(), clubId);
    }
}
