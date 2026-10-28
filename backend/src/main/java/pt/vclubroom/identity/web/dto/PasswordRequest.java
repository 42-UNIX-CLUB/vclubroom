package pt.vclubroom.identity.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** An admin's new temporary password for someone's local account. */
public record PasswordRequest(@NotBlank @Size(max = 128) String temporaryPassword) {
}
