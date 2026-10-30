package pt.vclubroom.support.application;

import java.util.List;
import pt.vclubroom.support.domain.TicketStatus;

/** Issues updated since the last read, with the status their state and labels mean, and comments added since. */
public record TrackerChanges(List<Issue> issues, List<Comment> comments) {

    public record Issue(int number, TicketStatus status) { }

    /** byTracker: written by the app's own account (the reporters' replies it passes on), never an answer. */
    public record Comment(long id, int issue, boolean byTracker, String body) { }
}
