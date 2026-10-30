package pt.vclubroom.support.application;

import java.time.Instant;
import java.util.UUID;
import pt.vclubroom.support.domain.MessageAuthor;
import pt.vclubroom.support.domain.TicketMessage;

/** One message as the reporter sees it. delivered: the team has it (a reply of theirs waits for the tracker otherwise). */
public record MessageView(UUID id, MessageAuthor author, String body, boolean delivered, Instant createdAt) {

    static MessageView of(TicketMessage m) {
        return new MessageView(m.id(), m.author(), m.body(), m.trackerCommentId() != null, m.createdAt());
    }
}
