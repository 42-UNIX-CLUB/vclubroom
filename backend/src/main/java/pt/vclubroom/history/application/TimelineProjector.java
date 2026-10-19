package pt.vclubroom.history.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import pt.vclubroom.club.api.ClubCatalog;
import pt.vclubroom.club.events.ClubApproved;
import pt.vclubroom.club.events.ClubArchived;
import pt.vclubroom.club.events.ClubCreationRequested;
import pt.vclubroom.history.domain.TimelineEntryType;
import pt.vclubroom.history.persistence.TimelineEntryRepository;
import pt.vclubroom.membership.events.OwnerAdded;
import pt.vclubroom.membership.events.OwnerRemoved;
import pt.vclubroom.membership.events.OwnerSteppedDown;

/**
 * Builds the timeline from club and membership events (history.md §6). Every handler is idempotent (dedup key).
 * Owner events carry no era: the club's current era is read when the event is projected. That is the era the fact
 * happened in, because eras only change at an archival or a revival, which owners cannot overlap with.
 * E14-S02 adds REVIVED (ClubRevived).
 */
@Component
class TimelineProjector {

    private final TimelineEntryRepository entries;
    private final ClubCatalog clubs;
    private final ObjectMapper objectMapper;

    TimelineProjector(TimelineEntryRepository entries, ClubCatalog clubs, ObjectMapper objectMapper) {
        this.entries = entries;
        this.clubs = clubs;
        this.objectMapper = objectMapper;
    }

    @ApplicationModuleListener
    void on(ClubCreationRequested e) {
        record(e.clubId(), 0, TimelineEntryType.CREATION_REQUESTED, e.founderId(), e.founderId(), Map.of(),
            e.occurredAt(), "CREATION_REQUESTED:" + e.clubId());
    }

    @ApplicationModuleListener
    void on(ClubApproved e) {
        record(e.clubId(), e.era(), TimelineEntryType.FOUNDED, e.approvedBy(), e.founderId(), Map.of(),
            e.occurredAt(), "FOUNDED:" + e.clubId());
    }

    @ApplicationModuleListener
    void on(ClubArchived e) {
        record(e.clubId(), e.endedEra(), TimelineEntryType.ARCHIVED, e.archivedBy(), null,
            Map.of("reason", e.reason() == null ? "" : e.reason()), e.occurredAt(),
            "ARCHIVED:" + e.clubId() + ":" + e.endedEra());
    }

    @ApplicationModuleListener
    void on(OwnerAdded e) {
        record(e.clubId(), currentEra(e.clubId()), TimelineEntryType.OWNER_ADDED, e.by(), e.userId(),
            Map.of("reason", e.reason()), e.occurredAt(),
            "OWNER_ADDED:" + e.clubId() + ":" + e.userId() + ":" + e.occurredAt());
    }

    @ApplicationModuleListener
    void on(OwnerSteppedDown e) {
        record(e.clubId(), currentEra(e.clubId()), TimelineEntryType.OWNER_STEPPED_DOWN, e.userId(), e.userId(),
            Map.of(), e.occurredAt(), "OWNER_STEPPED_DOWN:" + e.clubId() + ":" + e.userId() + ":" + e.occurredAt());
    }

    @ApplicationModuleListener
    void on(OwnerRemoved e) {
        record(e.clubId(), currentEra(e.clubId()), TimelineEntryType.OWNER_REMOVED, e.by(), e.userId(),
            Map.of("reason", e.reason()), e.occurredAt(),
            "OWNER_REMOVED:" + e.clubId() + ":" + e.userId() + ":" + e.occurredAt());
    }

    private int currentEra(UUID clubId) {
        return clubs.require(clubId).currentEra();
    }

    private void record(UUID clubId, int era, TimelineEntryType type, UUID actorId, UUID subjectId,
                        Map<String, Object> data, Instant occurredAt, String dedupKey) {
        entries.insertIfAbsent(UUID.randomUUID(), clubId, era, type.name(), actorId, subjectId, json(data),
            occurredAt, dedupKey);
    }

    private String json(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException(ex);
        }
    }
}
