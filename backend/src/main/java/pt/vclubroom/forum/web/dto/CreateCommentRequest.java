package pt.vclubroom.forum.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** parentId: the top-level comment this one replies to, or null. */
public record CreateCommentRequest(@NotBlank @Size(max = 5000) String body, UUID parentId) {
}
