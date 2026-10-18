package pt.vclubroom.club.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import pt.vclubroom.club.api.ClubStatus;
import pt.vclubroom.club.api.JoinPolicy;

/** Application-level view of a club, returned by the services and turned into ClubResponse by the web layer. */
public record ClubDetails(UUID id, String slug, String name, String tagline, String description, String category,
                          List<String> tags, String avatarUrl, String bannerUrl, ClubStatus status,
                          JoinPolicy joinPolicy, UUID founderId, int currentEra, String reviewNote,
                          Instant createdAt, List<Era> eras) {

    public record Era(int number, Instant startedAt, String startReason, Instant endedAt, String endReason) {
    }
}