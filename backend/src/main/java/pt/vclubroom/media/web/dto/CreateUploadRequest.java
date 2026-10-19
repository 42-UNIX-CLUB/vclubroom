package pt.vclubroom.media.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import pt.vclubroom.media.api.MediaPurpose;

public record CreateUploadRequest(@NotNull MediaPurpose purpose,
                                  @NotBlank @Size(max = 255) String fileName,
                                  @NotBlank String contentType,
                                  @Positive long sizeBytes) {
}
