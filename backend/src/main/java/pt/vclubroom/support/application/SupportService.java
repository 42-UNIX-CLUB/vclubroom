package pt.vclubroom.support.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.shared.error.BusinessRuleException;
import pt.vclubroom.shared.error.NotFoundException;
import pt.vclubroom.shared.security.Actor;
import pt.vclubroom.support.domain.Ticket;
import pt.vclubroom.support.domain.TicketMessage;
import pt.vclubroom.support.domain.TicketType;
import pt.vclubroom.support.events.TicketSubmitted;
import pt.vclubroom.support.persistence.TicketMessageRepository;
import pt.vclubroom.support.persistence.TicketRepository;

/**
 * Sending, reading and answering your own tickets. Saving is all these do: TrackerSyncJob passes tickets and replies on
 * to the tracker, so they work even while it is down. Nobody else's tickets are visible here; the team uses the tracker.
 */
@Service
public class SupportService {

    /** The browser only matters for these; the others don't store it (data minimisation). */
    private static final Set<TicketType> WITH_BROWSER = Set.of(TicketType.BUG, TicketType.ACCESSIBILITY);

    private final TicketRepository tickets;
    private final TicketMessageRepository messages;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final int dailyLimit;

    SupportService(TicketRepository tickets, TicketMessageRepository messages, ApplicationEventPublisher events, Clock clock,
                   @Value("${app.support.daily-limit:10}") int dailyLimit) {
        this.tickets = tickets;
        this.messages = messages;
        this.events = events;
        this.clock = clock;
        this.dailyLimit = dailyLimit;
    }

    @Transactional
    public TicketView submit(Actor actor, NewTicket command) {
        Instant now = clock.instant();
        if (tickets.countByReporterIdAndCreatedAtAfter(actor.userId(), now.minus(Duration.ofDays(1))) >= dailyLimit) {
            throw new BusinessRuleException("TICKET_LIMIT_REACHED",
                "You have sent " + dailyLimit + " tickets in the last 24 hours. Please try again tomorrow.");
        }
        Ticket ticket = tickets.save(Ticket.open(tickets.nextNumber(), actor.userId(), command.type(), command.title().strip(),
            command.description().strip(), appPath(command.pageUrl()),
            WITH_BROWSER.contains(command.type()) ? trim(command.userAgent(), 300) : null, now));
        events.publishEvent(new TicketSubmitted(ticket.id(), ticket.number(), ticket.ref(), ticket.reporterId(), ticket.title(), now));
        return TicketView.of(ticket);
    }

    @Transactional(readOnly = true)
    public List<TicketView> mine(Actor actor) {
        return tickets.findByReporterIdOrderByCreatedAtDesc(actor.userId()).stream().map(TicketView::of).toList();
    }

    @Transactional(readOnly = true)
    public TicketDetails get(Actor actor, long number) {
        Ticket ticket = mine(actor, number);
        return TicketDetails.of(ticket, messages.findByTicketIdOrderByCreatedAt(ticket.id()).stream().map(MessageView::of).toList());
    }

    /** A reply on my ticket, while it is open (E17-S03). */
    @Transactional
    public MessageView reply(Actor actor, long number, String body) {
        Ticket ticket = mine(actor, number);
        if (ticket.status().closed()) {
            throw new BusinessRuleException("TICKET_CLOSED", "This ticket is closed. Send a new one if you need more help.");
        }
        Instant now = clock.instant();
        ticket.reporterReplied(now);
        return MessageView.of(messages.save(TicketMessage.fromReporter(ticket.id(), body.strip(), now)));
    }

    private Ticket mine(Actor actor, long number) {
        return tickets.findByNumberAndReporterId(number, actor.userId())
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
