package pt.vclubroom.club.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Why this club should come back, and who will run it: the admin reads it before deciding. */
public record RevivalRequestBody(@NotBlank @Size(max = 2000) String motivation) {
}
