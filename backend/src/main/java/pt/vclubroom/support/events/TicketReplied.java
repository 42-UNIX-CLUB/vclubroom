package pt.vclubroom.support.events;

import java.time.Instant;
import java.util.UUID;

/** The team answered a ticket (a /reply comment in the tracker). */
public record TicketReplied(UUID ticketId, long number, String ref, UUID reporterId, String title, UUID messageId,
                            String body, Instant occurredAt) {
}
