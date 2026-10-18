package pt.vclubroom.club.application;

import org.springframework.stereotype.Component;

import pt.vclubroom.club.domain.Club;

/**
 * Builds ClubDetails from the aggregate. Avatar and banner URLs stay null until E08 (media), which resolves
 * club.avatarMediaId()/bannerMediaId() to URLs through media.api.MediaCatalog (club.md §9.3).
 */
@Component
class ClubDetailsAssembler {

    ClubDetails toDetails(Club club) {
        return new ClubDetails(club.id(), club.slug(), club.name(), club.tagline(), club.description(),
            club.category(), club.tags(), null, null,
            club.status(), club.joinPolicy(), club.founderId(), club.currentEra(), club.reviewNote(),
            club.createdAt(),
            club.eras().stream()
                .map(e -> new ClubDetails.Era(e.number(), e.startedAt(), e.startReason().name(),
                    e.endedAt(), e.endReason()))
                .toList());
    }
}