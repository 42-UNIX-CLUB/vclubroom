package pt.vclubroom.ops.application;

import java.time.Instant;

/** Events a listener hasn't finished handling: how many, and since when (the oldest). */
public record ListenerBacklog(String listener, String eventType, long count, Instant oldest) {
}
