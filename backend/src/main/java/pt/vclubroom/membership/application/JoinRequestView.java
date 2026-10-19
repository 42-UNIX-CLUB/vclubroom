package pt.vclubroom.membership.application;

import java.time.Instant;
import java.util.UUID;
import pt.vclubroom.identity.api.UserSummary;

/** A request in a club's queue. requester is null only if the user no longer exists in identity. */
public record JoinRequestView(UUID id, UUID userId, UserSummary requester, String message, Instant createdAt) {
}
