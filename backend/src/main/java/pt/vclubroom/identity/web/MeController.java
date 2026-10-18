package pt.vclubroom.identity.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pt.vclubroom.identity.application.UserDirectoryService;
import pt.vclubroom.identity.web.dto.MeResponse;
import pt.vclubroom.shared.security.AuthenticatedUser;

@RestController
@RequestMapping("/api/me")
class MeController {

    private final UserDirectoryService userDirectory;

    MeController(UserDirectoryService userDirectory) {
        this.userDirectory = userDirectory;
    }

    /** Both principals (42 and local) implement AuthenticatedUser, so this works for every login type. */
    @GetMapping
    MeResponse me(@AuthenticationPrincipal AuthenticatedUser me) {
        return userDirectory.requireMe(me.userId());
    }
}
