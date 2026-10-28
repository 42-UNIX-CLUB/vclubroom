package pt.vclubroom.membership.web.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Set;
import java.util.UUID;

/** The member's custom roles, all of them (an empty set removes them all). */
public record MemberRolesRequest(@NotNull Set<UUID> roleIds) {
}
