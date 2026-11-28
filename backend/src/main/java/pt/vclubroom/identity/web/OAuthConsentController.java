package pt.vclubroom.identity.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pt.vclubroom.identity.application.AuthorizedAppService;
import pt.vclubroom.identity.application.ConsentRequestView;
import pt.vclubroom.shared.security.AuthenticatedUser;

/** /api/oauth/consent (E20-S05): what the consent page needs to know about an app asking to be let in. */
@RestController
class OAuthConsentController {

    private final AuthorizedAppService apps;

    OAuthConsentController(AuthorizedAppService apps) {
        this.apps = apps;
    }

    @GetMapping("/api/oauth/consent")
    ConsentRequestView consent(@AuthenticationPrincipal AuthenticatedUser me, @RequestParam("client_id") String clientId,
                               @RequestParam(name = "scope", required = false) String scope) {
        return apps.consentRequest(me.userId(), clientId, scope);
    }
}
