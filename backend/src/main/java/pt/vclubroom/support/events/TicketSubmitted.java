package pt.vclubroom.support.events;

import java.time.Instant;
import java.util.UUID;

/** Someone sent a ticket. ref: "T-17". */
public record TicketSubmitted(UUID ticketId, long number, String ref, UUID reporterId, String title, Instant occurredAt) {
}
