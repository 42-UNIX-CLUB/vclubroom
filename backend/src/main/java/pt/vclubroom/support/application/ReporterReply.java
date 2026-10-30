package pt.vclubroom.support.application;

import java.util.UUID;

/** A reporter's message for the tracker. retry: an earlier attempt failed, so its comment may exist after all. */
public record ReporterReply(UUID messageId, String body, boolean retry) {
}
