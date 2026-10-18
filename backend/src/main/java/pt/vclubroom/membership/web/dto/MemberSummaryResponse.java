package pt.vclubroom.membership.web.dto;

import java.util.List;
import pt.vclubroom.identity.api.UserSummary;
import pt.vclubroom.membership.application.MemberSummary;

public record MemberSummaryResponse(long memberCount, List<UserSummary> owners) {

    public static MemberSummaryResponse from(MemberSummary s) {
        return new MemberSummaryResponse(s.memberCount(), s.owners());
    }
}
