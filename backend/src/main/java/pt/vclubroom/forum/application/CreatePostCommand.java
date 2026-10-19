package pt.vclubroom.forum.application;

import java.util.List;
import java.util.UUID;

public record CreatePostCommand(String title, String body, List<UUID> mediaIds) {
}
