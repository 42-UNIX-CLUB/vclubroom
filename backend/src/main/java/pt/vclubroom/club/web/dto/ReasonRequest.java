package pt.vclubroom.club.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of reject and archive: the reason is required and shown to the people concerned. */
public record ReasonRequest(@NotBlank @Size(max = 1000) String reason) {
}
