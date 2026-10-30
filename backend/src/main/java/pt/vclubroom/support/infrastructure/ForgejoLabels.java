package pt.vclubroom.support.infrastructure;

import java.util.Locale;
import pt.vclubroom.support.domain.TicketType;

/** The labels of deploy/forgejo/labels.txt the backend relies on: rename them in both places. */
final class ForgejoLabels {

    static final String FROM_APP = "source/app";

    private ForgejoLabels() {
    }

    /** BUG → "type/bug". */
    static String type(TicketType type) {
        return "type/" + type.name().toLowerCase(Locale.ROOT);
    }
}
