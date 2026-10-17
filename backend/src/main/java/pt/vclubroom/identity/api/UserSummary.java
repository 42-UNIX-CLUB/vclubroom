package pt.vclubroom.identity.api;

import java.util.UUID;

/** Public, non-sensitive view of a user, safe to show to any logged-in user. */
public record UserSummary(UUID id, String login, String displayName, String avatarUrl) {
}
