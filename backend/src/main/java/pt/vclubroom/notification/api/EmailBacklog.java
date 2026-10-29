package pt.vclubroom.notification.api;

/**
 * The email outbox at a glance (E15-S04): waiting for a first try, waiting to be retried, given up after 5 attempts
 * (R-27), sent in the last 24 h, and the most recent error.
 */
public record EmailBacklog(long waiting, long retrying, long failed, long sentLastDay, String lastError) {
}
