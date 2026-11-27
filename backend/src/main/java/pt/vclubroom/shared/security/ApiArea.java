package pt.vclubroom.shared.security;

import java.util.Locale;

/** The parts of the API a token can be given access to (E20-S01): one read and one write scope each, e.g. forum:read. */
public enum ApiArea {

    PROFILE("Your profile, and the people directory"),
    CLUBS("Clubs: their spaces, channels, members, roles and invitations"),
    FORUM("Posts, comments and reactions"),
    CHAT("Chat messages"),
    EVENTS("Events, RSVPs and calendars"),
    NOTIFICATIONS("Notifications and their preferences"),
    SUPPORT("Support tickets"),
    MEDIA("Uploads");

    private final String description;

    ApiArea(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String read() {
        return id() + ":read";
    }

    public String write() {
        return id() + ":write";
    }
}
