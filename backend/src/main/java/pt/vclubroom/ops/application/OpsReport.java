package pt.vclubroom.ops.application;

import java.time.Instant;
import java.util.List;
import pt.vclubroom.notification.api.EmailBacklog;

/** GET /api/admin/ops: the event outbox per listener, the email outbox, and when it was measured. */
public record OpsReport(List<ListenerBacklog> outbox, EmailBacklog emails, Instant measuredAt) {
}
