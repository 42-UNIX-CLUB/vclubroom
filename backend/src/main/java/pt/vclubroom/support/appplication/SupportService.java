package pt.vclubroom.support.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.shared.error.BusinessRuleException;
import pt.vclubroom.shared.error.NotFoundException;
import pt.vclubroom.shared.security.Actor;
import pt.vclubroom.support.domain.Ticket;
import pt.vclubroom.support.domain.TicketType;
import pt.vclubroom.support.persistence.TicketRepository;

/**
 * Sending and reading your own tickets. Saving is all a submission does: TrackerSyncJob sends it to the tracker, so
 * the form works even while the tracker is down. Nobody else's tickets are visible here; the team uses the tracker.
 */
@Service
public class SupportService {

    /** The browser only matters for these; the others don't store it (data minimisation). */
    private static final Set<TicketType> WITH_BROWSER = Set.of(TicketType.BUG, TicketType.ACCESSIBILITY);

    private final TicketRepository tickets;
    private final Clock clock;
    private final int dailyLimit;

    SupportService(TicketRepository tickets, Clock clock, @Value("${app.support.daily-limit:10}") int dailyLimit) {
        this.tickets = tickets;
        this.clock = clock;
        this.dailyLimit = dailyLimit;
    }

    @Transactional
    public pt.vclubroom.support.application.TicketView submit(Actor actor, pt.vclubroom.support.application.NewTicket command) {
        Instant now = clock.instant();
        if (tickets.countByReporterIdAndCreatedAtAfter(actor.userId(), now.minus(Duration.ofDays(1))) >= dailyLimit) {
            throw new BusinessRuleException("TICKET_LIMIT_REACHED",
                "You have sent " + dailyLimit + " tickets in the last 24 hours. Please try again tomorrow.");
        }
        Ticket ticket = Ticket.open(tickets.nextNumber(), actor.userId(), command.type(), command.title().strip(),
            command.description().strip(), appPath(command.pageUrl()),
            WITH_BROWSER.contains(command.type()) ? trim(command.userAgent(), 300) : null, now);
        return pt.vclubroom.support.application.TicketView.of(tickets.save(ticket));
    }

    @Transactional(readOnly = true)
    public List<pt.vclubroom.support.application.TicketView> mine(Actor actor) {
        return tickets.findByReporterIdOrderByCreatedAtDesc(actor.userId()).stream().map(pt.vclubroom.support.application.TicketView::of).toList();
    }

    @Transactional(readOnly = true)
    public pt.vclubroom.support.application.TicketView get(Actor actor, long number) {
        return tickets.findByNumberAndReporterId(number, actor.userId()).map(pt.vclubroom.support.application.TicketView::of)
            .orElseThrow(() -> new NotFoundException("TICKET_NOT_FOUND", "No such ticket."));
    }

    /** Only a page of this app ("/c/chess?tab=x"): anything else is dropped rather than passed on to the team. */
    private static String appPath(String pageUrl) {
        if (pageUrl == null || !pageUrl.startsWith("/") || pageUrl.startsWith("//")) {
            return null;
        }
        return trim(pageUrl, 500);
    }

    private static String trim(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.strip();
        return v.length() <= max ? v : v.substring(0, max);
    }
}
