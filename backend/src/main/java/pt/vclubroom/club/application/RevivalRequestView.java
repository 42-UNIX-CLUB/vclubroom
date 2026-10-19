package pt.vclubroom.club.application;

import java.time.Instant;
import java.util.UUID;

/** A revival request, with the club's and the requester's names for the admin queue. */
public record RevivalRequestView(UUID id, UUID clubId, String clubName, String clubSlug, UUID requesterId,
                                 String requesterName, String motivation, String status, String reviewNote,
                                 Instant createdAt) {
}
