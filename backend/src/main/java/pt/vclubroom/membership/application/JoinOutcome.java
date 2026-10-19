package pt.vclubroom.membership.application;

import java.util.UUID;

/** JOINED (open club, membershipId set) or REQUESTED (approval club, requestId set). */
public record JoinOutcome(Status status, UUID membershipId, UUID requestId) {

    public enum Status { JOINED, REQUESTED }

    static JoinOutcome joined(UUID membershipId) {
        return new JoinOutcome(Status.JOINED, membershipId, null);
    }

    static JoinOutcome requested(UUID requestId) {
        return new JoinOutcome(Status.REQUESTED, null, requestId);
    }
}
