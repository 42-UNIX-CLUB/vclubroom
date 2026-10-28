package pt.vclubroom.group.application;

import java.time.Instant;
import pt.vclubroom.identity.api.UserSummary;

/** Someone in a group, and since when. */
public record GroupMemberView(UserSummary user, Instant addedAt) {
}
