package pt.vclubroom.support.application;

import java.time.Instant;
import pt.vclubroom.support.domain.Ticket;
import pt.vclubroom.support.domain.TicketStatus;
import pt.vclubroom.support.domain.TicketType;

/** A ticket as its reporter sees it. */
public record TicketView(long number, String ref, TicketType type, String title, String description, String pageUrl,
                         TicketStatus status, Instant createdAt, Instant updatedAt) {

    static TicketView of(Ticket t) {
        return new TicketView(t.number(), t.ref(), t.type(), t.title(), t.description(), t.pageUrl(), t.status(),
            t.createdAt(), t.updatedAt());
    }
}
