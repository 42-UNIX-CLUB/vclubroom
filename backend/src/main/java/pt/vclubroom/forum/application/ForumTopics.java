package pt.vclubroom.forum.application;

import java.util.UUID;

/**
 * The forum's live destinations. A channel's topic carries what a feed shows (new posts, post reactions, comment
 * counts); a post's topic carries what its open thread shows (new comments, comment reactions). Payloads hold only
 * IDs and counts: the same for every subscriber, whoever they are.
 */
public final class ForumTopics {

    private ForumTopics() {
    }

    public static String channelPosts(UUID channelId) {
        return "/topic/channels/" + channelId + "/posts";
    }

    public static String post(UUID postId) {
        return "/topic/posts/" + postId;
    }
}
