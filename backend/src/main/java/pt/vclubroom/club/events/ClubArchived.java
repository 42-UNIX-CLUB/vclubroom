package pt.vclubroom.club.events;

import java.time.Instant;
import java.util.UUID;

/** Skeleton for E14-S01: ClubService.archive does not publish it yet. */
public record ClubArchived(UUID clubId, String name, int endedEra, UUID archivedBy, String reason, Instant occurredAt) {
}
