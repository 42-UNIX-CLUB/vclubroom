package pt.vclubroom.support.infrastructure;

import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pt.vclubroom.support.application.NewIssue;
import pt.vclubroom.support.application.ReporterReply;
import pt.vclubroom.support.application.TicketTracker;
import pt.vclubroom.support.application.TrackerChanges;

/**
 * TICKETS_TRACKER=log (the default): no tracker at all (development, tests). The issue number is the ticket's; nothing
 * ever changes on that side.
 */
@Component
@ConditionalOnProperty(name = "app.support.tracker", havingValue = "log", matchIfMissing = true)
class LoggingTicketTracker implements TicketTracker {

    private static final Logger log = LoggerFactory.getLogger(LoggingTicketTracker.class);

    @Override
    public int createIssue(NewIssue issue) {
        log.info("[tracker] {} ({})\n{}", issue.title(), issue.type(), issue.body());
        return Math.toIntExact(issue.number());
    }

    @Override
    public long addReporterReply(int issue, ReporterReply reply) {
        log.info("[tracker] comment on issue {}\n{}", issue, reply.body());
        return reply.messageId().getMostSignificantBits() & Long.MAX_VALUE;
    }

    @Override
    public TrackerChanges changesSince(Instant since) {
        return new TrackerChanges(List.of(), List.of());
    }
}
