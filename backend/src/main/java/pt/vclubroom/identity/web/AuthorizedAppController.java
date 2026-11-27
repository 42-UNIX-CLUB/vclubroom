package pt.vclubroom.identity.web;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pt.vclubroom.identity.application.AuthorizedAppService;
import pt.vclubroom.identity.application.AuthorizedAppView;
import pt.vclubroom.shared.security.AuthenticatedUser;

/** /api/me/authorized-apps (E20-S05): the apps you let in. From the app only, like tokens and apps. */
@RestController
@RequestMapping("/api/me/authorized-apps")
class AuthorizedAppController {

    private final AuthorizedAppService apps;

    AuthorizedAppController(AuthorizedAppService apps) {
        this.apps = apps;
    }

    @GetMapping
    List<AuthorizedAppView> list(@AuthenticationPrincipal AuthenticatedUser me) {
        return apps.list(me.userId());
    }

    @DeleteMapping("/{appId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("appId") String appId) {
        apps.revoke(me.userId(), appId);
    }
}
