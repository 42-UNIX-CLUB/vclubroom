package pt.vclubroom.support.application;

import pt.vclubroom.support.domain.TicketType;

/** A ticket as the form sends it. pageUrl: the app page it is about ("/c/chess"); userAgent: the browser. */
public record NewTicket(TicketType type, String title, String description, String pageUrl, String userAgent) {
}
