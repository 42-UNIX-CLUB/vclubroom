package pt.vclubroom.forum.web.dto;

import jakarta.validation.constraints.Size;

/** The new title (optional) and text; a post with attachments may have no text (EMPTY_POST otherwise). */
public record UpdatePostRequest(@Size(max = 140) String title, @Size(max = 20000) String body) {
}
