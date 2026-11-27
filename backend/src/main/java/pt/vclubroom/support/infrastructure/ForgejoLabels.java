package pt.vclubroom.support.infrastructure;

import java.util.Collection;
import java.util.Locale;
import pt.vclubroom.support.domain.TicketStatus;
import pt.vclubroom.support.domain.TicketType;
import pt.vclubroom.support.domain.WorkState;

/** The labels of deploy/forgejo/labels.txt and deploy/forgejo/development/labels.txt the backend relies on: rename them in both places. */
final class ForgejoLabels {

    static final String FROM_APP = "source/app";
    static final String IN_PROGRESS = "status/in-progress";
    static final String WAITING = "status/waiting-on-reporter";
    static final String WONT_FIX = "resolution/wont-fix";
    static final String DUPLICATE = "resolution/duplicate";
    static final String IN_REVIEW = "status/in-review";             // development only

    private ForgejoLabels() {
    }

    /** BUG → "type/bug". */
    static String type(TicketType type) {
        return "type/" + type.name().toLowerCase(Locale.ROOT);
    }

    /** What an issue's state and labels mean for its reporter (E17-S03). */
    static TicketStatus status(boolean open, Collection<String> labels) {
        if (!open) {
            return labels.contains(WONT_FIX) ? TicketStatus.DECLINED
                : labels.contains(DUPLICATE) ? TicketStatus.DUPLICATE : TicketStatus.RESOLVED;
        }
        return labels.contains(WAITING) ? TicketStatus.WAITING_ON_REPORTER
            : labels.contains(IN_PROGRESS) ? TicketStatus.IN_PROGRESS : TicketStatus.OPEN;
    }

    /** How far a development issue is, for the reporters of linked tickets (E18-S03). */
    static WorkState work(boolean open, Collection<String> labels) {
        if (!open) {
            return WorkState.DONE;
        }
        return labels.contains(IN_PROGRESS) || labels.contains(IN_REVIEW) ? WorkState.IN_PROGRESS : WorkState.PLANNED;
    }
}
