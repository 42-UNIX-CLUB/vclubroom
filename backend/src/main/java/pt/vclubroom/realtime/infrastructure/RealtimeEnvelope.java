package pt.vclubroom.realtime.infrastructure;

import java.time.Instant;

/** What a browser receives: {"type": "post.created", "payload": {…}, "sentAt": "…"}. */
public record RealtimeEnvelope(String type, Object payload, Instant sentAt) {
}
