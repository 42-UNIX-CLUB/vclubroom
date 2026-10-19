package pt.vclubroom.club.web.dto;

import java.time.Instant;
import java.util.UUID;
import pt.vclubroom.club.application.RevivalRequestView;

public record RevivalRequestResponse(UUID id, UUID clubId, String clubName, String clubSlug, UUID requesterId,
                                     String requesterName, String motivation, String status, String reviewNote,
                                     Instant createdAt) {

    public static RevivalRequestResponse from(RevivalRequestView v) {
        return new RevivalRequestResponse(v.id(), v.clubId(), v.clubName(), v.clubSlug(), v.requesterId(),
            v.requesterName(), v.motivation(), v.status(), v.reviewNote(), v.createdAt());
    }
}
