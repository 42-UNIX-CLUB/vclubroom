package pt.vclubroom.identity.web.dto;

import pt.vclubroom.identity.api.PlatformRole;
import pt.vclubroom.identity.domain.UserStatus;

/** Either or both; what is left out stays as it is. */
public record UpdateUserRequest(PlatformRole platformRole, UserStatus status) {
}
