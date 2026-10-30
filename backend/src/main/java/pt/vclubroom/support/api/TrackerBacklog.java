package pt.vclubroom.support.api;

import java.time.Instant;

/**
 * The tracker sync at a glance (E17-S04): tickets and replies the tracker doesn't have yet (and the oldest of them), when
 * its changes were last read, the most recent error, and where the team opens the tracker (empty if not configured).
 */
public record TrackerBacklog(long unsentTickets, long unsentReplies, Instant oldestUnsent, Instant lastRead,
                             String lastError, String trackerUrl) {
}
