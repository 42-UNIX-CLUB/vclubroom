package pt.vclubroom.club.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import pt.vclubroom.club.api.ClubStatus;
import pt.vclubroom.club.api.JoinPolicy;

/** Carries the full public profile so read models (discovery) never need to call back. */
public record ClubProfileUpdated(UUID clubId, String slug, String name, String tagline, String description,
                                 String category, List<String> tags, UUID avatarMediaId, UUID bannerMediaId,
                                 JoinPolicy joinPolicy, ClubStatus status, int currentEra, Instant occurredAt) {
}
