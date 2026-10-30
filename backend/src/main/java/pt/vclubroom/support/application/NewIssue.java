package pt.vclubroom.support.application;

import java.util.UUID;
import pt.vclubroom.support.domain.TicketType;

/**
 * What the tracker gets for a ticket. retry: an earlier attempt failed, so its issue may exist after all (a lost
 * response); the tracker looks for it before creating one.
 */
public record NewIssue(UUID ticketId, long number, TicketType type, String title, String body, boolean retry) {
}
