package pt.vclubroom.group.api;

import java.util.UUID;

public record GroupSummary(UUID id, GroupScope scope, UUID clubId, String name, String slug, boolean synced) {
}
