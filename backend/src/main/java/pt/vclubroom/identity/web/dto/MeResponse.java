package pt.vclubroom.identity.web.dto;

import java.util.UUID;

/** Body of GET /api/me and POST /api/auth/login. */
public record MeResponse(UUID id, String login, String displayName, String email,
                         String avatarUrl, String provider, boolean admin) {
}
