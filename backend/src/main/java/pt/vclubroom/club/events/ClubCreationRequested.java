package pt.vclubroom.club.events;

import java.time.Instant;
import java.util.UUID;

/** A student asked for a new club. Consumers: notification (admins to review, E12), history (E14). */
public record ClubCreationRequested(UUID clubId, String slug, String name, UUID founderId, Instant occurredAt) {
}