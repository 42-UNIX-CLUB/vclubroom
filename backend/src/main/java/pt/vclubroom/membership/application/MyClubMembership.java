package pt.vclubroom.membership.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import pt.vclubroom.club.api.ClubStatus;

/** One entry of GET /api/me/memberships: a club I belong to, with my roles there. */
public record MyClubMembership(UUID clubId, String slug, String name, ClubStatus status, boolean owner,
                               List<MembershipView.RoleView> roles, Instant joinedAt) {
}
