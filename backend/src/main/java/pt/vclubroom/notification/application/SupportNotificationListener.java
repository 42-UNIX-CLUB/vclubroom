package pt.vclubroom.notification.application;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import pt.vclubroom.notification.domain.NotificationType;
import pt.vclubroom.support.events.TicketReplied;
import pt.vclubroom.support.events.TicketStatusChanged;
import pt.vclubroom.support.events.TicketSubmitted;

/** Support tickets (E17-S03): only ever told to their reporter, as support emails (EmailDispatchJob). */
@Component
class SupportNotificationListener {

    private static final int EXCERPT = 500;

    private final NotificationDispatcher dispatcher;

    SupportNotificationListener(NotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @ApplicationModuleListener
    void on(TicketSubmitted e) {
        dispatcher.dispatch(new NotificationDraft(NotificationType.SUPPORT_TICKET_RECEIVED,
            e.ref() + " received: " + e.title(),
            "Thanks for writing to us. The team answers on the ticket's page, and you'll hear about it here.",
            link(e.number()), Map.of("ticket", e.ref()), "SUPPORT_TICKET_RECEIVED:" + e.ticketId()), Set.of(e.reporterId()));
    }

    @ApplicationModuleListener
    void on(TicketReplied e) {
        String body = e.body().length() <= EXCERPT ? e.body() : e.body().substring(0, EXCERPT) + "…";
        dispatcher.dispatch(new NotificationDraft(NotificationType.SUPPORT_TICKET_REPLIED,
            e.ref() + ": the team answered “" + e.title() + "”", body,
            link(e.number()), Map.of("ticket", e.ref()), "SUPPORT_TICKET_REPLIED:" + e.messageId()), Set.of(e.reporterId()));
    }

    @ApplicationModuleListener
    void on(TicketStatusChanged e) {
        boolean waiting = "WAITING_ON_REPORTER".equals(e.status());
        dispatcher.dispatch(new NotificationDraft(e.closed() ? NotificationType.SUPPORT_TICKET_CLOSED : NotificationType.SUPPORT_TICKET_UPDATED,
                e.closed() ? e.ref() + " is closed: " + e.statusLabel() : e.ref() + " is now " + e.statusLabel().toLowerCase(Locale.ROOT),
                waiting ? "The team needs your answer to go on with “" + e.title() + "”." : "“" + e.title() + "”",
                link(e.number()), Map.of("ticket", e.ref()), "SUPPORT_TICKET_STATUS:" + e.ticketId() + ":" + e.status() + ":" + e.occurredAt()),
            Set.of(e.reporterId()));
    }

    private static String link(long number) {
        return "/support/" + number;
    }
}
