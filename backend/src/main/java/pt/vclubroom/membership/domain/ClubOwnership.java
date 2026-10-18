package pt.vclubroom.membership.domain;

import pt.vclubroom.shared.error.BusinessRuleException;

/** Domain service guarding BR-04 (a club keeps at least one owner). Callers must hold the Owner-role row lock. */
public final class ClubOwnership {

    private ClubOwnership() {
    }

    public static void ensureAnotherOwnerRemains(long currentOwnerCount) {
        if (currentOwnerCount <= 1) {
            throw new BusinessRuleException("LAST_OWNER",
                "A club must keep at least one owner. Invite a co-owner first.");
        }
    }
}
