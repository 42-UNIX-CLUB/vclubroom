package pt.vclubroom.discovery.application;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import pt.vclubroom.club.api.ClubStatus;
import pt.vclubroom.club.events.ClubApproved;
import pt.vclubroom.club.events.ClubProfileUpdated;
import pt.vclubroom.discovery.persistence.ClubCardRepository;
import pt.vclubroom.forum.events.PostPublished;
import pt.vclubroom.membership.api.ClubAuthorization;
import pt.vclubroom.membership.events.MemberJoined;
import pt.vclubroom.membership.events.MemberLeft;

/**
 * Keeps discovery.club_cards up to date (discovery.md §7.1). Every handler is idempotent and safe out of order.
 * Later: E04-S04 resolves avatar and banner URLs (media's MediaCatalog).
 */
@Component
class ClubCardProjector {

    private final ClubCardRepository cards;
    private final ClubAuthorization clubAuthorization;

    ClubCardProjector(ClubCardRepository cards, ClubAuthorization clubAuthorization) {
        this.cards = cards;
        this.clubAuthorization = clubAuthorization;
    }

    @ApplicationModuleListener
    void on(ClubProfileUpdated e) {
        if (e.status() != ClubStatus.ACTIVE && e.status() != ClubStatus.ARCHIVED) {
            return;                                                              // BR-03
        }
        String searchDocument = String.join(" ", e.name(), nullToEmpty(e.tagline()),
            String.join(" ", e.tags()), nullToEmpty(e.description()));

        cards.upsertProfile(e.clubId(), e.slug(), e.name(), e.tagline(), e.description(), e.category(),
            e.tags().toArray(String[]::new), null, null, e.status().name(), e.joinPolicy().name(),
            e.currentEra(), searchDocument, e.occurredAt());
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
}
