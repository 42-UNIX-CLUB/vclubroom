package pt.vclubroom.discovery.web.dto;

import java.time.Instant;
import java.util.UUID;
import pt.vclubroom.discovery.persistence.DirectorySearchRepository.ClubCardRow;

/** One club in the directory or on the landing page (anonymous-safe fields only). */
public record ClubCardResponse(UUID clubId, String slug, String name, String tagline, String category,
                               String avatarUrl, String status, String joinPolicy, int memberCount,
                               Instant lastActivityAt, Instant foundedAt) {

    public static ClubCardResponse from(ClubCardRow r) {
        return new ClubCardResponse(r.clubId(), r.slug(), r.name(), r.tagline(), r.category(), r.avatarUrl(),
            r.status(), r.joinPolicy(), r.memberCount(), r.lastActivityAt(), r.foundedAt());
    }
}
