package pt.vclubroom.discovery.application;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import pt.vclubroom.club.api.ClubStatus;
import pt.vclubroom.club.events.ClubApproved;
import pt.vclubroom.club.events.ClubProfileUpdated;
import pt.vclubroom.discovery.persistence.ClubCardRepository;
import pt.vclubroom.forum.events.PostPublished;
import pt.vclubroom.media.api.MediaCatalog;
import pt.vclubroom.media.api.MediaView;
import pt.vclubroom.membership.api.ClubAuthorization;
import pt.vclubroom.membership.events.MemberJoined;
import pt.vclubroom.membership.events.MemberLeft;

/**
 * Keeps discovery.club_cards up to date (discovery.md §7.1). Every handler is idempotent and safe out of order.
 * Avatar and banner are public images: their URLs are stored on the card, so the directory never calls media.
 */
@Component
class ClubCardProjector {

    private final ClubCardRepository cards;
    private final ClubAuthorization clubAuthorization;
    private final MediaCatalog mediaCatalog;

    ClubCardProjector(ClubCardRepository cards, ClubAuthorization clubAuthorization, MediaCatalog mediaCatalog) {
        this.cards = cards;
        this.clubAuthorization = clubAuthorization;
        this.mediaCatalog = mediaCatalog;
    }

    @ApplicationModuleListener
    void on(ClubProfileUpdated e) {
        if (e.status() != ClubStatus.ACTIVE && e.status() != ClubStatus.ARCHIVED) {
            return;                                                              // BR-03
        }
        String searchDocument = String.join(" ", e.name(), nullToEmpty(e.tagline()),
            String.join(" ", e.tags()), nullToEmpty(e.description()));

        var ids = Stream.of(e.avatarMediaId(), e.bannerMediaId()).filter(Objects::nonNull).toList();
        Map<UUID, MediaView> media = ids.isEmpty() ? Map.of() : mediaCatalog.resolve(ids);
        cards.upsertProfile(e.clubId(), e.slug(), e.name(), e.tagline(), e.description(), e.category(),
            e.tags().toArray(String[]::new), url(media, e.avatarMediaId()), url(media, e.bannerMediaId()),
            e.status().name(), e.joinPolicy().name(), e.currentEra(), searchDocument, e.occurredAt());
        // Listeners may run out of order: recount here too, in case MemberJoined arrived before the card existed.
        cards.setMemberCount(e.clubId(), clubAuthorization.activeMemberIds(e.clubId()).size());
    }

    @ApplicationModuleListener
    void on(ClubApproved e) {
        cards.upsertFoundedAt(e.clubId(), e.slug(), e.name(), e.occurredAt());
    }

    /** Recounted, never incremented: a repeated event cannot make the count drift. */
    @ApplicationModuleListener
    void on(MemberJoined e) {
        cards.setMemberCount(e.clubId(), clubAuthorization.activeMemberIds(e.clubId()).size());
    }

    @ApplicationModuleListener
    void on(MemberLeft e) {
        cards.setMemberCount(e.clubId(), clubAuthorization.activeMemberIds(e.clubId()).size());
    }

    /** The "most active" sort of the directory and the landing page. */
    @ApplicationModuleListener
    void on(PostPublished e) {
        cards.bumpLastActivity(e.clubId(), e.occurredAt());
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String url(Map<UUID, MediaView> media, UUID id) {
        return id == null || !media.containsKey(id) ? null : media.get(id).url();
    }
}
