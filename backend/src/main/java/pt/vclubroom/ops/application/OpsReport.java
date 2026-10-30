package pt.vclubroom.ops.application;

import java.time.Instant;
import java.util.List;
import pt.vclubroom.notification.api.EmailBacklog;
import pt.vclubroom.support.api.TrackerBacklog;

/** GET /api/admin/ops: the event outbox per listener, the email outbox, the tracker sync, and when it was measured. */
public record OpsReport(List<ListenerBacklog> outbox, EmailBacklog emails, TrackerBacklog tickets, Instant measuredAt) {
}
