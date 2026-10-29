package pt.vclubroom.notification.api;

/** Read-only view of the email outbox, for the operations card (E15-S04). */
public interface EmailOutbox {

    EmailBacklog backlog();
}
