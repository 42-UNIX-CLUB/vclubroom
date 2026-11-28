package pt.vclubroom.notification.domain;

/** Every kind of notification, with its defaults (R-15): in-app on for all, email for what matters most. */
public enum NotificationType {
    CLUB_CREATION_SUBMITTED(true, false),
    CLUB_APPROVED(true, true),
    CLUB_REJECTED(true, true),
    CLUB_ARCHIVED(true, true),
    CLUB_REVIVAL_REQUESTED(true, false),
    CLUB_REVIVED(true, true),
    JOIN_REQUEST_RECEIVED(true, false),
    JOIN_REQUEST_DECIDED(true, true),
    OWNERSHIP_INVITATION_RECEIVED(true, true),
    OWNERSHIP_CHANGED(true, false),
    NEW_POST(true, false),
    NEW_COMMENT(true, false),
    NEW_REPLY(true, false),
    EVENT_SCHEDULED(true, false),
    EVENT_UPDATED(true, false),
    EVENT_CANCELLED(true, true),
    EVENT_REMINDER(true, true),
    SUPPORT_TICKET_RECEIVED(false, true),       // E17: about your own tickets, in support emails (EmailDispatchJob)
    SUPPORT_TICKET_REPLIED(true, true),
    SUPPORT_TICKET_UPDATED(true, false),
    SUPPORT_TICKET_CLOSED(true, true),
    ACCESS_TOKEN_EXPIRING(true, true);          // E20: a personal access token expires within a week

    private final boolean defaultInApp;
    private final boolean defaultEmail;

    NotificationType(boolean defaultInApp, boolean defaultEmail) {
        this.defaultInApp = defaultInApp;
        this.defaultEmail = defaultEmail;
    }

    public boolean defaultInApp() { return defaultInApp; }
    public boolean defaultEmail() { return defaultEmail; }

    /** About one of your support tickets: sent as a support email, from its own address (E17-S03). */
    public boolean support() { return name().startsWith("SUPPORT_"); }
}
