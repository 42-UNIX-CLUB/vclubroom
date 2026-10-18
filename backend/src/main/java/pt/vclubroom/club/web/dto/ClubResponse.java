package pt.vclubroom.club.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import pt.vclubroom.club.application.ClubDetails;

public record ClubResponse(UUID id, String slug, String name, String tagline, String description, String category,
                           List<String> tags, String avatarUrl, String bannerUrl, String status, String joinPolicy,
                           UUID founderId, int currentEra, String reviewNote, Instant createdAt,
                           List<ClubEraResponse> eras) {

    public static ClubResponse from(ClubDetails d) {
        return new ClubResponse(d.id(), d.slug(), d.name(), d.tagline(), d.description(), d.category(), d.tags(),
            d.avatarUrl(), d.bannerUrl(), d.status().name(), d.joinPolicy().name(), d.founderId(),
            d.currentEra(), d.reviewNote(), d.createdAt(),
            d.eras().stream().map(ClubEraResponse::from).toList());
    }
}