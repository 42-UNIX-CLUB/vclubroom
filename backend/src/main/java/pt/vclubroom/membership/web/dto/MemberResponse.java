package pt.vclubroom.membership.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import pt.vclubroom.membership.application.MemberView;
import pt.vclubroom.membership.application.MembershipView;

public record MemberResponse(UUID userId, String login, String displayName, String avatarUrl, boolean owner,
                             List<MembershipView.RoleView> roles, Instant joinedAt) {

    public static MemberResponse from(MemberView v) {
        return new MemberResponse(v.userId(), v.login(), v.displayName(), v.avatarUrl(), v.owner(), v.roles(), v.joinedAt());
    }
}
