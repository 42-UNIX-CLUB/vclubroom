package pt.vclubroom.support.application;

/** Port: where the team works on tickets. Forgejo in production (E17-S01); a log in development. */
public interface TicketTracker {

    /**
     * Creates the issue and returns its number; on a retry, returns the issue an earlier attempt created if there is one.
     *
     * @throws TrackerException when the tracker is unreachable or refuses (the job retries later)
     */
    int createIssue(NewIssue issue);
}
