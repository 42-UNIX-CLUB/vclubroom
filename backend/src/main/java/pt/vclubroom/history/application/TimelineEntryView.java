package pt.vclubroom.history.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import pt.vclubroom.history.domain.TimelineEntryType;
import pt.vclubroom.identity.api.UserSummary;

/** actor: who did it; subject: who it concerns (null when nobody, e.g. ARCHIVED). */
public record TimelineEntryView(UUID id, int era, TimelineEntryType type, UserSummary actor, UserSummary subject,
                                Map<String, Object> data, Instant occurredAt) {
}
