package pt.vclubroom.notification.application;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import pt.vclubroom.club.api.ClubCatalog;
import pt.vclubroom.club.api.ClubSnapshot;
import pt.vclubroom.identity.api.UserDirectory;
import pt.vclubroom.identity.api.UserSummary;
import pt.vclubroom.membership.api.ClubAuthorization;
import pt.vclubroom.membership.api.ClubPermission;
import pt.vclubroom.membership.events.JoinRequestDecided;
import pt.vclubroom.membership.events.JoinRequested;
import pt.vclubroom.membership.events.OwnerAdded;
import pt.vclubroom.membership.events.OwnerRemoved;
import pt.vclubroom.membership.events.OwnershipInvitationSent;
import pt.vclubroom.notification.domain.NotificationType;

/**
 * Joining and ownership. Approvers hear about requests, requesters about decisions, invitees about invitations.
 * Ownership changes tell the person concerned, never the one who did it.
 */
@Component
class MembershipNotificationListener {

    private final ClubAuthorization authorization;
    private final ClubCatalog clubs;
    private final UserDirectory users;
    private final NotificationDispatcher dispatcher;

    MembershipNotificationListener(ClubAuthorization authorization, ClubCatalog clubs, UserDirectory users,
                                   NotificationDispatcher dispatcher) {
        this.authorization = authorization;
        this.clubs = clubs;
        this.users = users;
        this.dispatcher = dispatcher;
    }

    @ApplicationModuleListener
    void on(JoinRequested e) {
        ClubSnapshot club = clubs.require(e.clubId());
        dispatcher.dispatch(new NotificationDraft(NotificationType.JOIN_REQUEST_RECEIVED,
            name(e.userId()) + " wants to join " + club.name(), null, "/c/" + club.slug() + "/settings/requests",
            Map.of("clubId", e.clubId()), "JOIN_REQUEST_RECEIVED:" + e.requestId()),
            authorization.holdersOf(e.clubId(), ClubPermission.MEMBER_APPROVE));
    }

    @ApplicationModuleListener
    void on(JoinRequestDecided e) {
        ClubSnapshot club = clubs.require(e.clubId());
        dispatcher.dispatch(new NotificationDraft(NotificationType.JOIN_REQUEST_DECIDED,
            e.approved() ? "You are now a member of " + club.name() : "Your request to join " + club.name() + " was declined",
            null, e.approved() ? "/c/" + club.slug() : "/clubs/" + club.slug() + "/about",
            Map.of("clubId", e.clubId()), "JOIN_REQUEST_DECIDED:" + e.requestId()), Set.of(e.userId()));
    }

    @ApplicationModuleListener
    void on(OwnershipInvitationSent e) {
        ClubSnapshot club = clubs.require(e.clubId());
        dispatcher.dispatch(new NotificationDraft(NotificationType.OWNERSHIP_INVITATION_RECEIVED,
            name(e.invitedBy()) + " invites you to become an owner of " + club.name(),
            e.transfer() ? "They step down when you accept. Answer in the banner at the top of the page."
                         : "Answer in the banner at the top of the page.",
            "/c/" + club.slug(), Map.of("clubId", e.clubId()), "OWNERSHIP_INVITATION_RECEIVED:" + e.invitationId()),
            Set.of(e.inviteeId()));
    }

    /** The founder's ownership is the approval itself; an accepted invitation tells the inviter; others the new owner. */
    @ApplicationModuleListener
    void on(OwnerAdded e) {
        if ("FOUNDER".equals(e.reason())) {
            return;
        }
        ClubSnapshot club = clubs.require(e.clubId());
        boolean accepted = "INVITATION".equals(e.reason());
        UUID recipient = accepted ? e.by() : e.userId();
        if (recipient == null) {
            return;
        }
        dispatcher.dispatch(new NotificationDraft(NotificationType.OWNERSHIP_CHANGED,
            accepted ? name(e.userId()) + " is now an owner of " + club.name() : "You are now an owner of " + club.name(),
            null, "/c/" + club.slug(), Map.of("clubId", e.clubId()),
            "OWNER_ADDED:" + e.clubId() + ":" + e.userId() + ":" + e.occurredAt()), Set.of(recipient));
    }

    @ApplicationModuleListener
    void on(OwnerRemoved e) {
        ClubSnapshot club = clubs.require(e.clubId());
        dispatcher.dispatch(new NotificationDraft(NotificationType.OWNERSHIP_CHANGED,
            "You are no longer an owner of " + club.name(), null, "/c/" + club.slug(), Map.of("clubId", e.clubId()),
            "OWNER_REMOVED:" + e.clubId() + ":" + e.userId() + ":" + e.occurredAt()), Set.of(e.userId()));
    }

    private String name(UUID userId) {
        return users.findById(userId).map(UserSummary::displayName).orElse("Someone");
    }
}
