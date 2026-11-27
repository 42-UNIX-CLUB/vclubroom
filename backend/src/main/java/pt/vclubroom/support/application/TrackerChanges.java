package pt.vclubroom.support.application;

import java.util.List;
import pt.vclubroom.support.domain.LinkedWork;
import pt.vclubroom.support.domain.TicketStatus;

/**
 * What changed in the tracker since the last read: support issues, with the status their state and labels mean and the
 * development issues they depend on now (E18-S03); comments added since; development issues changed since.
 */
public record TrackerChanges(List<Issue> issues, List<Comment> comments, List<LinkedWork> work) {

    public record Issue(int number, TicketStatus status, List<LinkedWork> links) { }

    /** byTracker: written by the app's own account (the reporters' replies it passes on), never an answer. */
    public record Comment(long id, int issue, boolean byTracker, String body) { }
}
