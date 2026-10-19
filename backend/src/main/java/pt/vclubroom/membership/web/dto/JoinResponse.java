package pt.vclubroom.membership.web.dto;

import java.util.UUID;
import pt.vclubroom.membership.application.JoinOutcome;

public record JoinResponse(JoinOutcome.Status status, UUID membershipId, UUID requestId) {

    public static JoinResponse from(JoinOutcome o) {
        return new JoinResponse(o.status(), o.membershipId(), o.requestId());
    }
}
