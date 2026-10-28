package pt.vclubroom.membership.web;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pt.vclubroom.membership.application.ClubRoleView;
import pt.vclubroom.membership.application.PermissionCatalog;
import pt.vclubroom.membership.application.RoleService;
import pt.vclubroom.membership.web.dto.MemberRolesRequest;
import pt.vclubroom.membership.web.dto.RoleRequest;
import pt.vclubroom.shared.error.InvalidRequestException;
import pt.vclubroom.shared.security.AuthenticatedUser;

/** Club roles and the permission catalogue (membership.md §7). */
@RestController
class RoleController {

    private final RoleService roles;

    RoleController(RoleService roles) {
        this.roles = roles;
    }

    @GetMapping("/api/permissions")
    List<PermissionCatalog.PermissionInfo> permissions() {
        return PermissionCatalog.entries();
    }

    @GetMapping("/api/clubs/{clubId}/roles")
    List<ClubRoleView> list(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("clubId") UUID clubId) {
        return roles.list(me.actor(), clubId);
    }

    @PostMapping("/api/clubs/{clubId}/roles")
    @ResponseStatus(HttpStatus.CREATED)
    ClubRoleView create(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("clubId") UUID clubId,
                        @Valid @RequestBody RoleRequest request) {
        if (request.name() == null || request.name().isBlank() || request.permissions() == null) {
            throw new InvalidRequestException("ROLE_INCOMPLETE", "A new role needs a name and its permissions.");
        }
        return roles.create(me.actor(), clubId, request.name(), request.color(), request.permissions());
    }

    @PatchMapping("/api/clubs/{clubId}/roles/{roleId}")
    ClubRoleView update(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("clubId") UUID clubId,
                        @PathVariable("roleId") UUID roleId, @Valid @RequestBody RoleRequest request) {
        return roles.update(me.actor(), clubId, roleId, request.name(), request.color(), request.permissions());
    }

    @DeleteMapping("/api/clubs/{clubId}/roles/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("clubId") UUID clubId,
                @PathVariable("roleId") UUID roleId) {
        roles.delete(me.actor(), clubId, roleId);
    }

    @PutMapping("/api/clubs/{clubId}/members/{userId}/roles")
    Set<UUID> assign(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("clubId") UUID clubId,
                     @PathVariable("userId") UUID userId, @Valid @RequestBody MemberRolesRequest request) {
        return roles.assign(me.actor(), clubId, userId, request.roleIds());
    }
}
