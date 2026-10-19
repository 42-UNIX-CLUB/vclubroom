package pt.vclubroom.membership.web.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** transfer = true: "transfer ownership", the inviter steps down when the invitee accepts. */
public record InviteOwnerRequest(@NotNull UUID inviteeId, boolean transfer) {
}
