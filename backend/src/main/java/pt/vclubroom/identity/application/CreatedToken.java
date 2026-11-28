package pt.vclubroom.identity.application;

/** A new token: its value, shown this once, and its details (E20-S02). */
public record CreatedToken(String token, TokenView details) {
}
