package pt.vclubroom.identity.application;

import java.util.Optional;

/** Recognises the tokens apps send in Authorization: Bearer (E20): personal access tokens, and apps' access tokens. */
public interface BearerTokens {

    /** Who the token acts as and its scopes, when this kind of token is valid now; empty otherwise. */
    Optional<AuthenticatedToken> authenticate(String rawToken);
}
