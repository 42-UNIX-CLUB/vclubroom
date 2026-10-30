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

    /** What changed since then (E17-S03): the issues' statuses, and their comments, the tracker's own included. */
    TrackerChanges changesSince(Instant since);
}
