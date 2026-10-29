package pt.vclubroom.group.application;

import java.util.UUID;

/** A campus group: synced ones follow 42 profiles (E13-S04), manual ones are kept by admins (E15-S03). */
public record CampusGroupView(UUID id, String name, String slug, String description, boolean synced, long memberCount) {
}
