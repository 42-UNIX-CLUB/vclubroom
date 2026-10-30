package pt.vclubroom.support.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.identity.api.UserDirectory;
import pt.vclubroom.identity.api.UserSummary;
import pt.vclubroom.support.domain.MessageAuthor;
import pt.vclubroom.support.domain.Ticket;
import pt.vclubroom.support.domain.TicketMessage;
import pt.vclubroom.support.domain.TicketStatus;
import pt.vclubroom.support.events.TicketReplied;
import pt.vclubroom.support.events.TicketStatusChanged;
import pt.vclubroom.support.persistence.TicketMessageRepository;
import pt.vclubroom.support.persistence.TicketRepository;
import pt.vclubroom.support.persistence.TrackerCursor;

/**
 * Keeps the app and the tracker in step. Sending (tickets, then reporters' replies) is safe with N instances (FOR UPDATE
 * SKIP LOCKED); its first failure ends the run, since the tracker is probably down. Reading asks the tracker what changed
 * since the cursor: one instance at a time (the cursor row is locked), with an overlap, as the tracker counts in whole
 * seconds. Asking instead of being told (webhooks) means nothing is lost while the backend restarts: Forgejo doesn't
 * retry a webhook that failed.
 */
@Component
class TrackerSyncJob {

    private static final Logger log = LoggerFactory.getLogger(TrackerSyncJob.class);
    private static final int BATCH = 20;
    private static final Duration OVERLAP = Duration.ofMinutes(2);

    private final TicketRepository tickets;
    private final TicketMessageRepository messages;
    private final TrackerCursor cursor;
    private final UserDirectory users;
    private final TicketTracker tracker;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final String publicUrl;

    TrackerSyncJob(TicketRepository tickets, TicketMessageRepository messages, TrackerCursor cursor, UserDirectory users,
                   TicketTracker tracker, ApplicationEventPublisher events, Clock clock, @Value("${app.public-url}") String publicUrl) {
        this.tickets = tickets;
        this.messages = messages;
        this.cursor = cursor;
        this.users = users;
        this.tracker = tracker;
        this.events = events;
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
                int issue = tracker.createIssue(new NewIssue(t.id(), t.number(), t.type(), IssueText.title(t),
                    IssueText.body(t, reporters.get(t.reporterId()), publicUrl), t.trackerAttempts() > 0));
                t.sentToTracker(issue, now);
            } catch (TrackerException e) {
                log.warn("Ticket {} not sent to the tracker (attempt {}): {}", t.ref(), t.trackerAttempts() + 1, e.getMessage());
                t.trackerFailed(e.getMessage(), now);
                break;
            }
        }
    }

    /** Reporters' replies, once their ticket's issue exists (E17-S03). */
    @Scheduled(fixedDelayString = "${app.support.sync-interval:PT30S}", initialDelayString = "${app.support.sync-interval:PT30S}")
    @Transactional
    public void sendReplies() {
        Instant now = clock.instant();
        for (TicketMessage m : messages.claimUnsent(now, BATCH)) {
            Ticket ticket = tickets.findById(m.ticketId()).orElseThrow();
            UserSummary reporter = users.findById(ticket.reporterId()).orElse(null);
            try {
                m.sentToTracker(tracker.addReporterReply(ticket.trackerIssue(),
                    new ReporterReply(m.id(), IssueText.reply(reporter, m.body()), m.trackerAttempts() > 0)));
            } catch (TrackerException e) {
                log.warn("Reply on {} not sent to the tracker (attempt {}): {}", ticket.ref(), m.trackerAttempts() + 1, e.getMessage());
                m.trackerFailed(e.getMessage(), now);
                break;
            }
        }
    }

    /**
     * The team's answers, then status changes (E17-S03). Each is told to the reporter once: changes read again are skipped.
     * Answers come first, so a question asked with the waiting label counts as the newest message.
     */
    @Scheduled(fixedDelayString = "${app.support.sync-interval:PT30S}", initialDelayString = "${app.support.sync-interval:PT30S}")
    @Transactional
    public void readTrackerChanges() {
        Optional<Instant> readUntil = cursor.lock();
        if (readUntil.isEmpty()) {
            return;                                                       // another instance is reading
        }
        Instant now = clock.instant();
        TrackerChanges changes;
        try {
            changes = tracker.changesSince(readUntil.get().minus(OVERLAP));
        } catch (TrackerException e) {
            log.warn("Could not read the tracker's changes: {}", e.getMessage());
            cursor.failed(e.getMessage());
            return;
        }
        for (TrackerChanges.Comment comment : changes.comments()) {
            if (comment.byTracker() || messages.existsByTrackerCommentId(comment.id())) {
                continue;
            }
            Optional<String> answer = TeamReplies.forReporter(comment.body());
            Optional<Ticket> ticket = tickets.findByTrackerIssue(comment.issue());
            if (answer.isPresent() && ticket.isPresent()) {
                Ticket t = ticket.get();
                TicketMessage m = messages.save(TicketMessage.fromTeam(t.id(), answer.get(), comment.id(), now));
                events.publishEvent(new TicketReplied(t.id(), t.number(), t.ref(), t.reporterId(), t.title(), m.id(), m.body(), now));
            }
        }
        for (TrackerChanges.Issue issue : changes.issues()) {
            tickets.findByTrackerIssue(issue.number())
                .filter(t -> issue.status() != TicketStatus.WAITING_ON_REPORTER || !reporterWroteLast(t))
                .filter(t -> t.changeStatus(issue.status(), now))
                .ifPresent(t -> events.publishEvent(new TicketStatusChanged(t.id(), t.number(), t.ref(), t.reporterId(), t.title(),
                    t.status().name(), t.status().label(), t.status().closed(), now)));
        }
        cursor.advance(now);
    }

    /** "Waiting on the reporter" while their answer is the newest message: the label is out of date, it goes with the reply. */
    private boolean reporterWroteLast(Ticket t) {
        return messages.findFirstByTicketIdOrderByCreatedAtDesc(t.id()).map(m -> m.author() == MessageAuthor.REPORTER).orElse(false);
    }
}
