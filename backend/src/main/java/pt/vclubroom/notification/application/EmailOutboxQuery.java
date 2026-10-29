package pt.vclubroom.notification.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.notification.api.EmailBacklog;
import pt.vclubroom.notification.api.EmailOutbox;

/** Counts on the outbox columns (09-deployment §12), in one query. */
@Service
class EmailOutboxQuery implements EmailOutbox {

    private final JdbcTemplate jdbc;

    EmailOutboxQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public EmailBacklog backlog() {
        int lastTry = EmailDispatchJob.MAX_ATTEMPTS;
        return jdbc.queryForObject("""
            select count(*) filter (where email_requested and email_sent_at is null and email_attempts = 0) as waiting,
                   count(*) filter (where email_requested and email_sent_at is null and email_attempts between 1 and ? - 1) as retrying,
                   count(*) filter (where email_requested and email_sent_at is null and email_attempts >= ?) as failed,
                   count(*) filter (where email_sent_at > now() - interval '1 day') as sent_last_day,
                   (select email_last_error from notification.notifications where email_last_error is not null
                     order by email_next_attempt_at desc nulls last limit 1) as last_error
            from notification.notifications
            where (email_requested and email_sent_at is null) or email_sent_at > now() - interval '1 day'
            """, (rs, row) -> new EmailBacklog(rs.getLong("waiting"), rs.getLong("retrying"), rs.getLong("failed"),
            rs.getLong("sent_last_day"), rs.getString("last_error")), lastTry, lastTry);
    }
}
