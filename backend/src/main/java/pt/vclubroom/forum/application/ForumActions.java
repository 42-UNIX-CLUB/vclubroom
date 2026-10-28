package pt.vclubroom.forum.application;

import java.util.UUID;
import pt.vclubroom.space.api.ChannelAccess;

/**
 * Who may change a post or a comment (forum.md §7, E09-S04). The services enforce it; the views show it.
 * - edit: the author, who can still write in the channel;
 * - delete: the author as above, or a moderator (CONTENT_MODERATE);
 * - moderate (pin): a moderator.
 * Nothing changes once deleted, or in an earlier era (BR-15): history is read-only for everyone, as in chat.
 */
record ForumActions(boolean edit, boolean delete, boolean moderate) {

    static ForumActions of(ChannelAccess access, int era, UUID authorId, UUID viewerId, boolean deleted) {
        if (deleted || !access.isCurrentEra(era)) {
            return new ForumActions(false, false, false);
        }
        boolean author = authorId.equals(viewerId) && access.canWrite();
        return new ForumActions(author, author || access.canModerate(), access.canModerate());
    }
}
