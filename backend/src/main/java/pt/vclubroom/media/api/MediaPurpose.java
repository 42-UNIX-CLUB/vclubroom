package pt.vclubroom.media.api;

import java.util.EnumSet;
import java.util.Set;

/** What a file is for: decides its bucket (public or private) and which kinds of file are allowed. */
public enum MediaPurpose {
    POST_ATTACHMENT(false, EnumSet.allOf(MediaKind.class)),
    CHAT_ATTACHMENT(false, EnumSet.allOf(MediaKind.class)),
    CLUB_AVATAR(true, EnumSet.of(MediaKind.IMAGE)),
    CLUB_BANNER(true, EnumSet.of(MediaKind.IMAGE)),
    EVENT_COVER(true, EnumSet.of(MediaKind.IMAGE));

    private final boolean publicAccess;
    private final Set<MediaKind> allowedKinds;

    MediaPurpose(boolean publicAccess, Set<MediaKind> allowedKinds) {
        this.publicAccess = publicAccess;
        this.allowedKinds = allowedKinds;
    }

    public boolean isPublic() { return publicAccess; }
    public boolean allows(MediaKind kind) { return allowedKinds.contains(kind); }
}
