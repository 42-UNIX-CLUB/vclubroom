package pt.vclubroom.support.application;

import java.time.Instant;

/**
 * Port: where the team works on tickets. Forgejo in production (E17-S01); a log in development. Every call may throw
 * TrackerException when the tracker is unreachable or refuses; the job then tries again later.
 */
public interface TicketTracker {

    /** Creates the issue and returns its number; on a retry, returns the issue an earlier attempt created if there is one. */
    int createIssue(NewIssue issue);

    /**
     * Adds the reporter's reply to the issue as a comment and takes the issue off "waiting on reporter". Returns the
     * comment's ID; on a retry, the comment an earlier attempt created if there is one.
     */
    long addReporterReply(int issue, ReporterReply reply);

    /**
     * What changed since then: the support issues' statuses and the development issues they depend on (E17-S03, E18-S03),
     * their comments, the tracker's own included, and the development issues that changed.
     */
    TrackerChanges changesSince(Instant since);
}
