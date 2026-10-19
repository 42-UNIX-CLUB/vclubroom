package pt.vclubroom.chat.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import pt.vclubroom.chat.application.SendMessageCommand;

public record SendMessageRequest(@Size(max = 4000) String body, @Size(max = 10) List<UUID> mediaIds,
                                 @NotNull UUID clientNonce) {

    public SendMessageCommand toCommand() {
        return new SendMessageCommand(body, mediaIds == null ? List.of() : mediaIds, clientNonce);
    }
}
