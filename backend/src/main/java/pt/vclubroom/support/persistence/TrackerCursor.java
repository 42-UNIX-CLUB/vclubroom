package pt.vclubroom.support.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** support.tracker_cursor: how far the tracker has been read. Locking the row makes one instance read at a time. */
@Repository
public class TrackerCursor {

    private final JdbcTemplate jdbc;

    TrackerCursor(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Within a transaction: the cursor, or empty while another instance holds it. */
    public Optional<Instant> lock() {
        List<Timestamp> row = jdbc.queryForList(
            "select read_until from support.tracker_cursor where id = 1 for update skip locked", Timestamp.class);
        return row.stream().findFirst().map(Timestamp::toInstant);
    }

    public void advance(Instant readUntil) {
        jdbc.update("update support.tracker_cursor set read_until = ?, last_error = null where id = 1", Timestamp.from(readUntil));
    }

    public void failed(String error) {
        jdbc.update("update support.tracker_cursor set last_error = ? where id = 1", error);
    }
}
