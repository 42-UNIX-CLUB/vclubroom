package pt.vclubroom.membership.application;

import java.time.Instant;
import java.util.UUID;
import pt.vclubroom.identity.api.UserSummary;

/** A pending ownership invitation, for the invitee's inbox or the club's Ownership tab. */
public record InvitationView(UUID id, UUID clubId, String clubSlug, String clubName, UserSummary invitee,
                             UserSummary invitedBy, boolean transfer, Instant expiresAt) {
}
