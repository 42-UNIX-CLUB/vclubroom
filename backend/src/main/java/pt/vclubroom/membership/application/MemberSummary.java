package pt.vclubroom.membership.application;

import java.util.List;
import pt.vclubroom.identity.api.UserSummary;

/** What anyone may know about a club's people: how many members, and who the owners are. */
public record MemberSummary(long memberCount, List<UserSummary> owners) {
}
