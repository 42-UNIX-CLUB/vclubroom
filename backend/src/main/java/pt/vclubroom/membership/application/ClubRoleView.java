package pt.vclubroom.membership.application;

import java.util.Set;
import java.util.UUID;
import pt.vclubroom.membership.api.ClubPermission;

/** A role as the role editor shows it: Owner first, custom roles, Member last. */
public record ClubRoleView(UUID id, String name, String color, boolean system, boolean owner,
                           Set<ClubPermission> permissions, long memberCount) {
}
