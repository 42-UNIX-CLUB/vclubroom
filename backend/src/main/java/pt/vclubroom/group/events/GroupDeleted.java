package pt.vclubroom.group.events;

import java.time.Instant;
import java.util.UUID;
import pt.vclubroom.group.api.GroupScope;

/** space removes the group from its rules; a GROUPS rule left without groups lets nobody in (fail closed). */
public record GroupDeleted(UUID groupId, GroupScope scope, UUID clubId, Instant occurredAt) {
}
