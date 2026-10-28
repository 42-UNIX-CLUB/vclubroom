package pt.vclubroom.space.web.dto;

import java.util.UUID;

/** What a new space or channel is called in URLs. */
public record CreatedResponse(UUID id, String slug) {
}
