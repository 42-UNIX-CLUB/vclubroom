package pt.vclubroom.support.domain;

/**
 * What the reporter sees, in their words. RECEIVED until the tracker issue exists; then the issue decides: open, a
 * status/… label, or closed (with a resolution/… label to say why).
 */
public enum TicketStatus {
    RECEIVED("Received"),
    OPEN("Open"),
    IN_PROGRESS("In progress"),
    WAITING_ON_REPORTER("Waiting for you"),
    RESOLVED("Resolved"),
    DECLINED("Won't do"),
    DUPLICATE("Duplicate");

    private final String label;

    TicketStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean closed() {
        return this == RESOLVED || this == DECLINED || this == DUPLICATE;
    }
}
