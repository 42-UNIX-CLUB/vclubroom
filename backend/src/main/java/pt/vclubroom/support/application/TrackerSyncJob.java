package pt.vclubroom.support.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.identity.api.UserDirectory;
import pt.vclubroom.identity.api.UserSummary;
import pt.vclubroom.support.domain.Ticket;
import pt.vclubroom.support.persistence.TicketRepository;

/**
 * The tracker outbox worker. Safe with N instances (FOR UPDATE SKIP LOCKED). The first failure ends the run: the
 * tracker is probably down, so the other tickets wait for the next run instead of each waiting for a timeout.
 */
@Component
class TrackerSyncJob {

    private static final Logger log = LoggerFactory.getLogger(TrackerSyncJob.class);
    private static final int BATCH = 20;

    private final TicketRepository tickets;
    private final UserDirectory users;
    private final pt.vclubroom.support.application.TicketTracker tracker;
    private final Clock clock;
    private final String publicUrl;

    TrackerSyncJob(TicketRepository tickets, UserDirectory users, pt.vclubroom.support.application.TicketTracker tracker, Clock clock,
                   @Value("${app.public-url}") String publicUrl) {
        this.tickets = tickets;
        this.users = users;
        this.tracker = tracker;
        this.clock = clock;
        this.publicUrl = publicUrl.replaceAll("/+$", "");
    }

    @Scheduled(fixedDelayString = "${app.support.sync-interval:PT30S}", initialDelayString = "${app.support.sync-interval:PT30S}")
    @Transactional
    public void sendTickets() {
        Instant now = clock.instant();
        List<Ticket> due = tickets.claimUnsent(now, BATCH);
        Map<UUID, UserSummary> reporters = users.findByIds(due.stream().map(Ticket::reporterId).toList());
        for (Ticket t : due) {
            try {
                int issue = tracker.createIssue(new NewIssue(t.id(), t.number(), t.type(), pt.vclubroom.support.application.IssueText.title(t),
                    IssueText.body(t, reporters.get(t.reporterId()), publicUrl), t.trackerAttempts() > 0));
                t.sentToTracker(issue, now);
            } catch (TrackerException e) {
                log.warn("Ticket {} not sent to the tracker (attempt {}): {}", t.ref(), t.trackerAttempts() + 1, e.getMessage());
                t.trackerFailed(e.getMessage(), now);
                break;
            }
        }
    }
}
