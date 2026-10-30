package pt.vclubroom.support.domain;

/**
 * What the reporter sees. RECEIVED until the tracker issue exists; then the issue decides: open, a status/… label, or
 * closed (with a resolution/… label to say why).
 */
public enum TicketStatus {
    RECEIVED,
    OPEN,
    IN_PROGRESS,
    WAITING_ON_REPORTER,
    RESOLVED,
    DECLINED,
    DUPLICATE;

    public boolean closed() {
        return this == RESOLVED || this == DECLINED || this == DUPLICATE;
    }
}
