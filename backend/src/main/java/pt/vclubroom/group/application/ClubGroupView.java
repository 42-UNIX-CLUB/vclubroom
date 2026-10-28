package pt.vclubroom.group.application;

import java.util.UUID;

/** A club group in the settings and in the access-rule picker. */
public record ClubGroupView(UUID id, String name, String slug, String description, long memberCount) {
}
