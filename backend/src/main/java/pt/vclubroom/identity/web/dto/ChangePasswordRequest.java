package pt.vclubroom.identity.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank @Size(max = 128) String newPassword) {
}
