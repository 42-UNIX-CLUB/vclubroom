package pt.vclubroom.identity.web;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pt.vclubroom.identity.application.AccessTokenService;
import pt.vclubroom.identity.application.CreatedToken;
import pt.vclubroom.identity.application.TokenView;
import pt.vclubroom.identity.web.dto.CreateTokenRequest;
import pt.vclubroom.shared.security.AuthenticatedUser;

/** /api/me/tokens (E20-S02): your personal access tokens. From the app only: tokens can't manage tokens (ApiScopes). */
@RestController
@RequestMapping("/api/me/tokens")
class TokenController {

    private final AccessTokenService tokens;

    TokenController(AccessTokenService tokens) {
        this.tokens = tokens;
    }

    @GetMapping
    List<TokenView> list(@AuthenticationPrincipal AuthenticatedUser me) {
        return tokens.list(me.userId());
    }

    /** The response holds the token's value: the only time it is shown. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CreatedToken create(@AuthenticationPrincipal AuthenticatedUser me, @Valid @RequestBody CreateTokenRequest request) {
        return tokens.create(me.userId(), request.name(), request.scopes(), request.expiresAt());
    }

    @DeleteMapping("/{tokenId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("tokenId") UUID tokenId) {
        tokens.revoke(me.userId(), tokenId);
    }
}
