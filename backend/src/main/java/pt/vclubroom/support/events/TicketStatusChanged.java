package pt.vclubroom.support.events;

import java.time.Instant;
import java.util.UUID;

/** The tracker changed what the reporter sees. statusLabel is their wording ("In progress", "Won't do"). */
public record TicketStatusChanged(UUID ticketId, long number, String ref, UUID reporterId, String title, String status,
                                  String statusLabel, boolean closed, Instant occurredAt) {
}
