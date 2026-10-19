package pt.vclubroom.chat.application;

import java.util.List;
import java.util.UUID;

/** clientNonce: made by the browser before sending, the same on every retry of this message. */
public record SendMessageCommand(String body, List<UUID> mediaIds, UUID clientNonce) {
}
