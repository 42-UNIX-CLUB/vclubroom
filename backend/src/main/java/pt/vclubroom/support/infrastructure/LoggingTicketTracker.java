package pt.vclubroom.support.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pt.vclubroom.support.application.NewIssue;
import pt.vclubroom.support.application.TicketTracker;

/** TICKETS_TRACKER=log (the default): no tracker at all (development, tests). The issue number is the ticket's. */
@Component
@ConditionalOnProperty(name = "app.support.tracker", havingValue = "log", matchIfMissing = true)
class LoggingTicketTracker implements TicketTracker {

    private static final Logger log = LoggerFactory.getLogger(LoggingTicketTracker.class);

    @Override
    public int createIssue(NewIssue issue) {
        log.info("[tracker] {} ({})\n{}", issue.title(), issue.type(), issue.body());
        return Math.toIntExact(issue.number());
    }
}
