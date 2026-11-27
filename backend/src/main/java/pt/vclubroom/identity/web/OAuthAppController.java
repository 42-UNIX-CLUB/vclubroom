package pt.vclubroom.identity.web;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pt.vclubroom.identity.application.AppView;
import pt.vclubroom.identity.application.OAuthAppService;
import pt.vclubroom.identity.application.RegisteredApp;
import pt.vclubroom.identity.web.dto.AppRequest;
import pt.vclubroom.shared.security.AuthenticatedUser;

/** /api/me/apps (E20-S04): the OAuth apps you registered. From the app only: tokens can't manage apps (ApiScopes). */
@RestController
@RequestMapping("/api/me/apps")
class OAuthAppController {

    private final OAuthAppService apps;

    OAuthAppController(OAuthAppService apps) {
        this.apps = apps;
    }

    @GetMapping
    List<AppView> mine(@AuthenticationPrincipal AuthenticatedUser me) {
        return apps.mine(me.userId());
    }

    /** A confidential app's secret is in the response: the only time it is shown. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    RegisteredApp register(@AuthenticationPrincipal AuthenticatedUser me, @RequestBody AppRequest request) {
        return apps.register(me.userId(), request.details(), request.confidential());
    }

    @PatchMapping("/{appId}")
    AppView update(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("appId") String appId, @RequestBody AppRequest request) {
        return apps.update(me.userId(), appId, request.details());
    }

    @PostMapping("/{appId}/secret")
    RegisteredApp newSecret(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("appId") String appId) {
        return apps.newSecret(me.userId(), appId);
    }

    @DeleteMapping("/{appId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("appId") String appId) {
        apps.delete(me.userId(), appId);
    }
}
