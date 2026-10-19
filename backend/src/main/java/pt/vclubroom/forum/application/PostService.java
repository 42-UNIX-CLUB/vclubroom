package pt.vclubroom.forum.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.forum.domain.Post;
import pt.vclubroom.forum.events.PostPublished;
import pt.vclubroom.forum.persistence.PostRepository;
import pt.vclubroom.media.api.MediaCatalog;
import pt.vclubroom.media.api.MediaPurpose;
import pt.vclubroom.shared.security.Actor;
import pt.vclubroom.space.api.ChannelAccess;
import pt.vclubroom.space.api.ChannelType;
import pt.vclubroom.space.api.SpaceAccessPolicy;

/**
 * Writing posts (forum.md §8.1). Access is decided by the space module, never here.
 * E10-S01 adds the real-time push of new posts; E09-S04 edit, delete and pin.
 */
@Service
public class PostService {

    private final PostRepository posts;
    private final SpaceAccessPolicy spaceAccess;
    private final MediaCatalog mediaCatalog;
    private final PostAssembler assembler;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    PostService(PostRepository posts, SpaceAccessPolicy spaceAccess, MediaCatalog mediaCatalog,
                PostAssembler assembler, ApplicationEventPublisher events, Clock clock) {
        this.posts = posts;
        this.spaceAccess = spaceAccess;
        this.mediaCatalog = mediaCatalog;
        this.assembler = assembler;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public PostView createPost(Actor actor, UUID channelId, CreatePostCommand cmd) {
        ChannelAccess access = spaceAccess.forChannel(channelId, actor)
            .requireType(ChannelType.FORUM)
            .requireWrite();                                                // CHANNEL_WRITE_DENIED
        List<UUID> mediaIds = cmd.mediaIds() == null ? List.of() : cmd.mediaIds();
        Instant now = clock.instant();
        Post post = Post.publish(access.channelId(), access.spaceId(), access.clubId(), access.currentEra(),
            actor.userId(), cmd.title(), cmd.body(), mediaIds, now);           // EMPTY_POST
        mediaCatalog.attach(mediaIds, actor.userId(), MediaPurpose.POST_ATTACHMENT);   // BR-19, same transaction
        posts.save(post);

        events.publishEvent(new PostPublished(post.id(), channelId, access.spaceId(), access.clubId(),
            actor.userId(), post.era(), post.title(), post.excerpt(), now));
        return assembler.toViews(List.of(post), access, actor.userId()).getFirst();
    }
}
