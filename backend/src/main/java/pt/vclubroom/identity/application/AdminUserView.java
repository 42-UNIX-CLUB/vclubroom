package pt.vclubroom.identity.application;

import java.time.Instant;
import java.util.UUID;
import pt.vclubroom.identity.api.PlatformRole;
import pt.vclubroom.identity.domain.AuthProvider;
import pt.vclubroom.identity.domain.UserStatus;

/** A user as the admin console lists them. */
public record AdminUserView(UUID id, String login, String displayName, String email, AuthProvider provider,
                            PlatformRole role, UserStatus status, Instant createdAt, Instant lastLoginAt) {
}
