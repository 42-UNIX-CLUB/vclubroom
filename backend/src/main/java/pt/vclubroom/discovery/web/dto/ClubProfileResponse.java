package pt.vclubroom.discovery.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import pt.vclubroom.discovery.domain.ClubCard;

/** The public club profile (R-24). */
public record ClubProfileResponse(UUID clubId, String slug, String name, String tagline, String description,
                                  String category, List<String> tags, String avatarUrl, String bannerUrl,
                                  String status, String joinPolicy, int currentEra, int memberCount,
                                  Instant foundedAt, Instant lastActivityAt) {

    public static ClubProfileResponse from(ClubCard c) {
        return new ClubProfileResponse(c.clubId(), c.slug(), c.name(), c.tagline(), c.description(), c.category(),
            c.tags(), c.avatarUrl(), c.bannerUrl(), c.status(), c.joinPolicy(), c.currentEra(), c.memberCount(),
            c.foundedAt(), c.lastActivityAt());
    }
}
