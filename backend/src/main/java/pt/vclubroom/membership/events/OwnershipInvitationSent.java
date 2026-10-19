package pt.vclubroom.membership.events;

import java.time.Instant;
import java.util.UUID;

/** Notification tells the invitee (E12). transfer: the inviter steps down when it is accepted. */
public record OwnershipInvitationSent(UUID invitationId, UUID clubId, UUID inviteeId, UUID invitedBy,
                                      boolean transfer, Instant expiresAt, Instant occurredAt) {
}
