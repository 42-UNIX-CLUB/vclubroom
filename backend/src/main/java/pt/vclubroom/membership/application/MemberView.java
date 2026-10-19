package pt.vclubroom.membership.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A row of the member list: who, their roles (Owner first), since when. */
public record MemberView(UUID userId, String login, String displayName, String avatarUrl, boolean owner,
                         List<MembershipView.RoleView> roles, Instant joinedAt) {
}
