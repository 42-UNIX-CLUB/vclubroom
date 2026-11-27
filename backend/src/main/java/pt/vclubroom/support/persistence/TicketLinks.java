package pt.vclubroom.support.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import pt.vclubroom.support.domain.LinkedWork;
import pt.vclubroom.support.domain.WorkState;

/** support.ticket_links: the copy of each ticket's linked development issues. */
@Repository
public class TicketLinks {

    private final JdbcTemplate jdbc;

    TicketLinks(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<LinkedWork> of(UUID ticketId) {
        return jdbc.query("select issue, title, state, milestone, released from support.ticket_links where ticket_id = ? order by issue",
            (rs, row) -> new LinkedWork(rs.getInt("issue"), rs.getString("title"), WorkState.valueOf(rs.getString("state")),
                rs.getString("milestone"), rs.getBoolean("released")), ticketId);
    }

    /** What the support issue depends on now: links that are gone go, new ones come. */
    public void replace(UUID ticketId, List<LinkedWork> work, Instant now) {
        jdbc.update("delete from support.ticket_links where ticket_id = ?", ticketId);
        jdbc.batchUpdate("""
            insert into support.ticket_links (ticket_id, issue, title, state, milestone, released, updated_at)
            values (?, ?, ?, ?, ?, ?, ?)""", work.stream().map(w -> new Object[] {ticketId, w.issue(), w.title(),
            w.state().name(), w.milestone(), w.released(), Timestamp.from(now)}).toList());
    }

    /** A development issue changed: every ticket linked to it gets its new title, progress and release. */
    public void update(LinkedWork work, Instant now) {
        jdbc.update("update support.ticket_links set title = ?, state = ?, milestone = ?, released = ?, updated_at = ? where issue = ?",
            work.title(), work.state().name(), work.milestone(), work.released(), Timestamp.from(now), work.issue());
    }
}
