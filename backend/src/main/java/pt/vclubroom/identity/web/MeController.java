package pt.vclubroom.identity.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pt.vclubroom.identity.application.LocalAccountService;
import pt.vclubroom.identity.application.UserDirectoryService;
import pt.vclubroom.identity.web.dto.ChangePasswordRequest;
import pt.vclubroom.identity.web.dto.MeResponse;
import pt.vclubroom.shared.security.AuthenticatedUser;

@RestController
@RequestMapping("/api/me")
class MeController {

    private final UserDirectoryService userDirectory;
    private final LocalAccountService accounts;

    MeController(UserDirectoryService userDirectory, LocalAccountService accounts) {
        this.userDirectory = userDirectory;
        this.accounts = accounts;
    }

    /** Both principals (42 and local) implement AuthenticatedUser, so this works for every login type. */
    @GetMapping
    MeResponse me(@AuthenticationPrincipal AuthenticatedUser me) {
        return userDirectory.requireMe(me.userId());
    }

    /** Local accounts only (E15-S02): this browser stays logged in, the others are logged out. */
    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changePassword(@AuthenticationPrincipal AuthenticatedUser me, @Valid @RequestBody ChangePasswordRequest request,
                        HttpServletRequest http) {
        HttpSession session = http.getSession(false);
        accounts.changeOwnPassword(me.userId(), request.currentPassword(), request.newPassword(),
            session == null ? null : session.getId());
    }
}
