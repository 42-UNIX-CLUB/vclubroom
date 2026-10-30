package pt.vclubroom.support.application;

import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.support.api.TrackerBacklog;
import pt.vclubroom.support.api.TrackerQueue;

/** Counts on the outbox columns and the cursor, in one query. */
@Service
class TrackerQueueQuery implements TrackerQueue {

    private final JdbcTemplate jdbc;
    private final String trackerUrl;

    TrackerQueueQuery(JdbcTemplate jdbc, @Value("${app.support.public-url:}") String trackerUrl) {
        this.jdbc = jdbc;
        this.trackerUrl = trackerUrl;
    }

    @Override
    @Transactional(readOnly = true)
    public TrackerBacklog backlog() {
        return jdbc.queryForObject("""
            with unsent as (
                select created_at, tracker_last_error as error, tracker_next_attempt_at as tried, 'ticket' as kind
                from support.tickets where tracker_issue is null
                union all
                select created_at, tracker_last_error, tracker_next_attempt_at, 'reply'
                from support.ticket_messages where author = 'REPORTER' and tracker_comment_id is null)
            select (select count(*) from unsent where kind = 'ticket') as tickets,
                   (select count(*) from unsent where kind = 'reply') as replies,
                   (select min(created_at) from unsent) as oldest,
                   c.read_until,
                   coalesce(c.last_error, (select error from unsent where error is not null order by tried desc nulls last limit 1)) as last_error
            from support.tracker_cursor c
            """, (rs, row) -> new TrackerBacklog(rs.getLong("tickets"), rs.getLong("replies"), instant(rs.getTimestamp("oldest")),
            instant(rs.getTimestamp("read_until")), rs.getString("last_error"), trackerUrl));
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
