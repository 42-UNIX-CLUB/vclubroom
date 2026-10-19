package pt.vclubroom.forum.web.dto;

import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import pt.vclubroom.forum.application.CreatePostCommand;

/** Text, media or both (an empty post is refused by the domain: 400 EMPTY_POST). */
public record CreatePostRequest(@Size(max = 140) String title,
                                @Size(max = 20000) String body,
                                @Size(max = 10) List<UUID> mediaIds) {

    public CreatePostCommand toCommand() {
        return new CreatePostCommand(title, body, mediaIds == null ? List.of() : mediaIds);
    }
}
