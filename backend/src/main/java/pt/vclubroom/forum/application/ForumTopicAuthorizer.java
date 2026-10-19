package pt.vclubroom.forum.application;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.forum.persistence.PostRepository;
import pt.vclubroom.realtime.spi.SubscriptionAuthorizer;
import pt.vclubroom.shared.error.NotFoundException;
import pt.vclubroom.shared.security.Actor;
import pt.vclubroom.space.api.ChannelType;
import pt.vclubroom.space.api.SpaceAccessPolicy;

/** Realtime SPI: only readers of a forum channel may listen to it, or to one of its posts. */
@Component
class ForumTopicAuthorizer implements SubscriptionAuthorizer {

    private static final Pattern CHANNEL_POSTS = Pattern.compile("^/topic/channels/([0-9a-f-]{36})/posts$");
    private static final Pattern POST = Pattern.compile("^/topic/posts/([0-9a-f-]{36})$");

    private final SpaceAccessPolicy spaceAccess;
    private final PostRepository posts;

    ForumTopicAuthorizer(SpaceAccessPolicy spaceAccess, PostRepository posts) {
        this.spaceAccess = spaceAccess;
        this.posts = posts;
    }

    @Override
    public boolean supports(String destination) {
        return CHANNEL_POSTS.matcher(destination).matches() || POST.matcher(destination).matches();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canSubscribe(Actor actor, String destination) {
        try {
            Matcher channel = CHANNEL_POSTS.matcher(destination);
            if (channel.matches()) {
                return readsForum(actor, UUID.fromString(channel.group(1)));
            }
            Matcher post = POST.matcher(destination);
            return post.matches() && posts.findById(UUID.fromString(post.group(1)))
                .map(p -> readsForum(actor, p.channelId()))
                .orElse(false);
        } catch (NotFoundException | IllegalArgumentException e) {
            return false;                                   // unknown channel or malformed ID: deny
        }
    }

    private boolean readsForum(Actor actor, UUID channelId) {
        var access = spaceAccess.forChannel(channelId, actor);
        return access.type() == ChannelType.FORUM && access.canRead();
    }
}
