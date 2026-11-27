package pt.vclubroom.identity.application;

/** A new app, or a new secret for one: the secret is shown this once (null for public apps). */
public record RegisteredApp(AppView app, String clientSecret) {
}
