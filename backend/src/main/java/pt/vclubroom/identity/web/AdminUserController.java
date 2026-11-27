package pt.vclubroom.identity.web;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pt.vclubroom.identity.application.AdminUserView;
import pt.vclubroom.identity.application.AccessTokenService;
import pt.vclubroom.identity.application.LocalAccountService;
import pt.vclubroom.identity.web.dto.CreateLocalAccountRequest;
import pt.vclubroom.identity.web.dto.PasswordRequest;
import pt.vclubroom.identity.web.dto.UpdateUserRequest;
import pt.vclubroom.shared.security.AuthenticatedUser;
import pt.vclubroom.shared.web.PageResponse;

/** /api/admin/users (identity.md §7, E15-S02). SecurityConfig limits /api/admin/** to admins. */
@RestController
@RequestMapping("/api/admin/users")
class AdminUserController {

    private final LocalAccountService accounts;
    private final AccessTokenService tokens;

    AdminUserController(LocalAccountService accounts, AccessTokenService tokens) {
        this.accounts = accounts;
        this.tokens = tokens;
    }

    @GetMapping
    PageResponse<AdminUserView> list(@RequestParam(name = "query", required = false) String query,
                                     @RequestParam(name = "page", defaultValue = "0") int page,
                                     @RequestParam(name = "size", defaultValue = "20") int size) {
        return accounts.list(query, Math.max(page, 0), Math.clamp(size, 1, 100));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AdminUserView create(@Valid @RequestBody CreateLocalAccountRequest request) {
        return accounts.create(request.toAccount());
    }

    @PatchMapping("/{userId}")
    AdminUserView update(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("userId") UUID userId,
                         @RequestBody UpdateUserRequest request) {
        return accounts.update(me.actor(), userId, request.platformRole(), request.status());
    }

    @PostMapping("/{userId}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void resetPassword(@PathVariable("userId") UUID userId, @Valid @RequestBody PasswordRequest request) {
        accounts.resetPassword(userId, request.temporaryPassword());
    }

    /** Every personal access token of this person stops working (E20-S02). */
    @DeleteMapping("/{userId}/tokens")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revokeTokens(@PathVariable("userId") UUID userId) {
        tokens.revokeAll(userId);
    }
}
