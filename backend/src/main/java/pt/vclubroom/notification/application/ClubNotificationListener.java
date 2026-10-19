package pt.vclubroom.notification.application;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import pt.vclubroom.club.api.ClubCatalog;
import pt.vclubroom.club.events.ClubApproved;
import pt.vclubroom.club.events.ClubArchived;
import pt.vclubroom.club.events.ClubCreationRequested;
import pt.vclubroom.club.events.ClubRejected;
import pt.vclubroom.identity.api.UserDirectory;
import pt.vclubroom.membership.api.ClubAuthorization;
import pt.vclubroom.notification.domain.NotificationType;

/** A club's lifecycle: admins review, founders hear the decision, members hear about an archival. */
@Component
class ClubNotificationListener {

    private final UserDirectory users;
    private final ClubAuthorization authorization;
    private final ClubCatalog clubs;
    private final NotificationDispatcher dispatcher;

    ClubNotificationListener(UserDirectory users, ClubAuthorization authorization, ClubCatalog clubs,
                             NotificationDispatcher dispatcher) {
        this.users = users;
        this.authorization = authorization;
        this.clubs = clubs;
        this.dispatcher = dispatcher;
    }

    @ApplicationModuleListener
    void on(ClubCreationRequested e) {
        dispatcher.dispatch(new NotificationDraft(NotificationType.CLUB_CREATION_SUBMITTED, "New club request: " + e.name(),
            "Waiting for your review.", "/admin/approvals", Map.of("clubId", e.clubId()),
            "CLUB_CREATION_SUBMITTED:" + e.clubId()), Set.copyOf(users.adminIds()));
    }

    @ApplicationModuleListener
    void on(ClubApproved e) {
        dispatcher.dispatch(new NotificationDraft(NotificationType.CLUB_APPROVED, e.name() + " is approved",
            "Your club is open: invite your friends!", "/c/" + e.slug(), Map.of("clubId", e.clubId()),
            "CLUB_APPROVED:" + e.clubId()), Set.of(e.founderId()));
    }

    @ApplicationModuleListener
    void on(ClubRejected e) {
        dispatcher.dispatch(new NotificationDraft(NotificationType.CLUB_REJECTED, e.name() + " was not approved",
            e.reason(), "/clubs/new", Map.of("clubId", e.clubId()), "CLUB_REJECTED:" + e.clubId()), Set.of(e.founderId()));
    }

    @ApplicationModuleListener
    void on(ClubArchived e) {
        Set<UUID> members = new HashSet<>(authorization.activeMemberIds(e.clubId()));
        members.remove(e.archivedBy());
        String link = clubs.findById(e.clubId()).map(c -> "/c/" + c.slug()).orElse("/clubs");
        dispatcher.dispatch(new NotificationDraft(NotificationType.CLUB_ARCHIVED, e.name() + " was archived", e.reason(),
            link, Map.of("clubId", e.clubId()), "CLUB_ARCHIVED:" + e.clubId() + ":" + e.endedEra()), members);
    }
}
