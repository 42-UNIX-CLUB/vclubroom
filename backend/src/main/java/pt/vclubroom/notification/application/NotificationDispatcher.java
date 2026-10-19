package pt.vclubroom.notification.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.realtime.api.RealtimePublisher;

/**
 * How to tell people (notification.md §8): preferences, one idempotent batch insert, and a live push to the
 * people whose row was really inserted (a redelivered event pushes nothing). Pushes leave after the commit.
 */
@Service
public class NotificationDispatcher {

    private static final String INSERT = """
        insert into notification.notifications
            (id, recipient_id, type, title, body, link, data, dedup_key, in_app, email_requested, created_at)
        values (?, ?, ?, ?, ?, ?, cast(? as jsonb), ?, ?, ?, ?)
        on conflict (recipient_id, dedup_key) do nothing
        """;

    private final JdbcTemplate jdbc;
    private final PreferenceService preferences;
    private final RealtimePublisher realtime;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    NotificationDispatcher(JdbcTemplate jdbc, PreferenceService preferences, RealtimePublisher realtime,
                           ObjectMapper objectMapper, Clock clock) {
        this.jdbc = jdbc;
        this.preferences = preferences;
        this.realtime = realtime;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** Joins the listener's transaction. */
    @Transactional
    public void dispatch(NotificationDraft draft, Collection<UUID> recipients) {
        if (recipients.isEmpty()) {
            return;
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        String data = toJson(draft.data());
        List<Object[]> rows = new ArrayList<>();
        List<NotificationView> views = new ArrayList<>();
        List<UUID> viewRecipients = new ArrayList<>();
        preferences.effective(recipients, draft.type()).forEach((userId, p) -> {
            if (!p.inApp() && !p.email()) {
                return;                                                   // this person muted the type
            }
            UUID id = UUID.randomUUID();
            rows.add(new Object[] {id, userId, draft.type().name(), draft.title(), draft.body(), draft.link(), data,
                draft.dedupKey(), p.inApp(), p.email(), Timestamp.from(now)});
            views.add(p.inApp() ? new NotificationView(id, draft.type(), draft.title(), draft.body(), draft.link(), false, now) : null);
            viewRecipients.add(userId);
        });
        int[] inserted = jdbc.batchUpdate(INSERT, rows);
        for (int i = 0; i < inserted.length; i++) {
            if (inserted[i] > 0 && views.get(i) != null) {                // 0: already there (a redelivery)
                realtime.toUser(viewRecipients.get(i), "/queue/notifications", "notification.created", views.get(i));
            }
        }
    }

    private String toJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data == null ? Map.of() : data);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Notification data not serializable", e);
        }
    }
}
