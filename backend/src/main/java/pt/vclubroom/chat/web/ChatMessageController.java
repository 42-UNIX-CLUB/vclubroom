package pt.vclubroom.chat.web;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pt.vclubroom.chat.application.ChatMessageView;
import pt.vclubroom.chat.application.ChatPage;
import pt.vclubroom.chat.application.ChatService;
import pt.vclubroom.chat.web.dto.EditMessageRequest;
import pt.vclubroom.chat.web.dto.SendMessageRequest;
import pt.vclubroom.shared.security.AuthenticatedUser;

/** Sending is REST, receiving is the push (R-03). A retried POST answers the message it already created. */
@RestController
class ChatMessageController {

    private final ChatService chat;

    ChatMessageController(ChatService chat) {
        this.chat = chat;
    }

    @GetMapping("/api/channels/{channelId}/messages")
    ChatPage history(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("channelId") UUID channelId,
                     @RequestParam(name = "before", required = false) Long before,
                     @RequestParam(name = "era", required = false) Integer era,
                     @RequestParam(name = "limit", defaultValue = "50") int limit) {
        return chat.history(me.actor(), channelId, era, before, Math.clamp(limit, 1, 100));
    }

    @PostMapping("/api/channels/{channelId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    ChatMessageView send(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("channelId") UUID channelId,
                         @Valid @RequestBody SendMessageRequest request) {
        return chat.send(me.actor(), channelId, request.toCommand());
    }

    @PatchMapping("/api/messages/{messageId}")
    ChatMessageView edit(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("messageId") long messageId,
                         @Valid @RequestBody EditMessageRequest request) {
        return chat.edit(me.actor(), messageId, request.body());
    }

    @DeleteMapping("/api/messages/{messageId}")
    ChatMessageView delete(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("messageId") long messageId) {
        return chat.delete(me.actor(), messageId);
    }
}
