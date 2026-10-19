package pt.vclubroom.club.application;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import pt.vclubroom.club.domain.Club;
import pt.vclubroom.media.api.MediaCatalog;
import pt.vclubroom.media.api.MediaView;

/** Builds ClubDetails from the aggregate; avatar and banner IDs become public URLs through media (club.md §9.3). */
@Component
class ClubDetailsAssembler {

    private final MediaCatalog mediaCatalog;

    ClubDetailsAssembler(MediaCatalog mediaCatalog) {
        this.mediaCatalog = mediaCatalog;
    }

    ClubDetails toDetails(Club club) {
        var ids = Stream.of(club.avatarMediaId(), club.bannerMediaId()).filter(Objects::nonNull).toList();
        Map<UUID, MediaView> media = ids.isEmpty() ? Map.of() : mediaCatalog.resolve(ids);
        return new ClubDetails(club.id(), club.slug(), club.name(), club.tagline(), club.description(),
            club.category(), club.tags(), url(media, club.avatarMediaId()), url(media, club.bannerMediaId()),
            club.status(), club.joinPolicy(), club.founderId(), club.currentEra(), club.reviewNote(),
            club.createdAt(),
            club.eras().stream()
                .map(e -> new ClubDetails.Era(e.number(), e.startedAt(), e.startReason().name(),
                    e.endedAt(), e.endReason()))
                .toList());
    }

    private static String url(Map<UUID, MediaView> media, UUID id) {
        return id == null || !media.containsKey(id) ? null : media.get(id).url();
    }
}
