package pt.vclubroom.ops.application;

import java.time.Clock;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.notification.api.EmailOutbox;
import pt.vclubroom.support.api.TrackerQueue;

/**
 * The operations report (09-deployment §12, 06-messaging §8). Incomplete event publications are normal for a moment
 * (listeners run after the commit); some that stay, or grow, mean a listener keeps failing: its logs say why, and
 * EventPublicationMaintenance retries them every minute.
 */
@Service
public class OpsService {

    private final JdbcTemplate jdbc;
    private final EmailOutbox emails;
    private final TrackerQueue tickets;
    private final Clock clock;

    OpsService(JdbcTemplate jdbc, EmailOutbox emails, TrackerQueue tickets, Clock clock) {
        this.jdbc = jdbc;
        this.emails = emails;
        this.tickets = tickets;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public OpsReport report() {
        List<ListenerBacklog> outbox = jdbc.query("""
            select listener_id, event_type, count(*) as pending, min(publication_date) as oldest
            from public.event_publication where completion_date is null
            group by listener_id, event_type order by oldest
            """, (rs, row) -> new ListenerBacklog(shortName(rs.getString("listener_id")), shortName(rs.getString("event_type")),
            rs.getLong("pending"), rs.getTimestamp("oldest").toInstant()));
        return new OpsReport(outbox, emails.backlog(), tickets.backlog(), clock.instant());
    }

    /** "pt.vclubroom.notification.application.ForumNotificationListener.on(pt.vclubroom…PostPublished)" → "ForumNotificationListener.on". */
    public static String shortName(String qualified) {
        String head = qualified.contains("(") ? qualified.substring(0, qualified.indexOf('(')) : qualified;
        String[] parts = head.split("\\.");
        return parts.length >= 2 && Character.isLowerCase(parts[parts.length - 1].charAt(0))
            ? parts[parts.length - 2] + "." + parts[parts.length - 1]
            : parts[parts.length - 1];
    }
}
