package pt.vclubroom.identity.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Set;

/** expiresAt null: the token never expires. */
public record CreateTokenRequest(@NotBlank @Size(max = 80) String name, @NotEmpty Set<String> scopes, Instant expiresAt) {
}
