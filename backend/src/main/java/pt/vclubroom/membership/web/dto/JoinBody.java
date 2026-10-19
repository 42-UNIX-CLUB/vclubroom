package pt.vclubroom.membership.web.dto;

import jakarta.validation.constraints.Size;

/** Optional note for the owners of an APPROVAL club. */
public record JoinBody(@Size(max = 500) String message) {
}
