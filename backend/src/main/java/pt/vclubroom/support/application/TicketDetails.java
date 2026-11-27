package pt.vclubroom.support.application;

import java.time.Instant;
import java.util.List;
import pt.vclubroom.support.domain.Ticket;
import pt.vclubroom.support.domain.TicketStatus;
import pt.vclubroom.support.domain.TicketType;

/** One ticket with its conversation (E17-S03) and the development work it is linked to (E18-S03). canReply: until it is closed. */
public record TicketDetails(long number, String ref, TicketType type, String title, String description, String pageUrl,
                            TicketStatus status, Instant createdAt, Instant updatedAt, List<MessageView> messages,
                            boolean canReply, List<LinkedWorkView> linkedWork) {

    static TicketDetails of(Ticket t, List<MessageView> messages, List<LinkedWorkView> linkedWork) {
        return new TicketDetails(t.number(), t.ref(), t.type(), t.title(), t.description(), t.pageUrl(), t.status(),
            t.createdAt(), t.updatedAt(), messages, !t.status().closed(), linkedWork);
    }
}
