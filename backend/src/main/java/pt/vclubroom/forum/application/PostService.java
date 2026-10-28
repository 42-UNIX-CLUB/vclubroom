package pt.vclubroom.forum.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.forum.domain.Post;
import pt.vclubroom.forum.events.PostPublished;
import pt.vclubroom.forum.persistence.PostRepository;
import pt.vclubroom.media.api.MediaCatalog;
import pt.vclubroom.media.api.MediaPurpose;
import pt.vclubroom.realtime.api.RealtimePublisher;
import pt.vclubroom.shared.error.BusinessRuleException;
import pt.vclubroom.shared.error.ForbiddenException;
import pt.vclubroom.shared.error.NotFoundException;
import pt.vclubroom.shared.security.Actor;
import pt.vclubroom.space.api.ChannelAccess;
import pt.vclubroom.space.api.ChannelType;
import pt.vclubroom.space.api.SpaceAccessPolicy;

/**
 * Writing posts (forum.md §8.1). Access is decided by the space module, never here; who may change a post is
 * ForumActions. Readers of the channel are told at once (after the commit), with IDs only.
 */
@Service
public class PostService {

    private final PostRepository posts;
    private final SpaceAccessPolicy spaceAccess;
    private final MediaCatalog mediaCatalog;
    private final PostAssembler assembler;
    private final ApplicationEventPublisher events;
    private final RealtimePublisher realtime;
    private final Clock clock;

    PostService(PostRepository posts, SpaceAccessPolicy spaceAccess, MediaCatalog mediaCatalog,
                PostAssembler assembler, ApplicationEventPublisher events, RealtimePublisher realtime, Clock clock) {
        this.posts = posts;
        this.spaceAccess = spaceAccess;
        this.mediaCatalog = mediaCatalog;
        this.assembler = assembler;
        this.events = events;
        this.realtime = realtime;
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
        realtime.toTopic(ForumTopics.channelPosts(channelId), "post.created", Map.of("postId", post.id()));
        return assembler.toViews(List.of(post), access, actor.userId()).getFirst();
    }

    /** The author, who can still write in the channel, in the current era. The attachments stay. */
    @Transactional
    public PostView edit(Actor actor, UUID postId, String title, String body) {
        Post post = load(postId);
        ChannelAccess access = spaceAccess.forChannel(post.channelId(), actor).requireRead();
        if (!post.authorId().equals(actor.userId())) {
            throw new ForbiddenException("NOT_AUTHOR", "Only the author can edit this post.");
        }
        requireCurrentEra(access, post);
        access.requireWrite();                                                     // CHANNEL_WRITE_DENIED
        post.edit(actor.userId(), title, body, clock.instant());                    // POST_DELETED, EMPTY_POST
        pushChange(post, "post.updated");
        return assembler.toViews(List.of(post), access, actor.userId()).getFirst();
    }

    /** The author as for editing, or a moderator (CONTENT_MODERATE). A tombstone stays in its place (BR-16). */
    @Transactional
    public void delete(Actor actor, UUID postId) {
        Post post = load(postId);
        ChannelAccess access = spaceAccess.forChannel(post.channelId(), actor).requireRead();
        boolean author = post.authorId().equals(actor.userId()) && access.canWrite();
        if (!author && !access.canModerate()) {
            throw new ForbiddenException("CANNOT_DELETE", "You cannot delete this post.");
        }
        requireCurrentEra(access, post);
        post.softDelete(actor.userId(), clock.instant());                           // POST_DELETED; unpins it
        pushChange(post, "post.deleted");
    }

    /** Moderators pin a post above the channel's others, or unpin it. */
    @Transactional
    public void pin(Actor actor, UUID postId, boolean pinned) {
        Post post = load(postId);
        ChannelAccess access = spaceAccess.forChannel(post.channelId(), actor).requireRead();
        if (!access.canModerate()) {
            throw new ForbiddenException("CANNOT_PIN", "Only moderators can pin posts.");
        }
        requireCurrentEra(access, post);
        post.pin(pinned);                                                           // POST_DELETED
        pushChange(post, "post.updated");
    }

    private Post load(UUID postId) {
        return posts.findById(postId).orElseThrow(() -> new NotFoundException("POST_NOT_FOUND", "Post not found"));
    }

    private static void requireCurrentEra(ChannelAccess access, Post post) {
        if (!access.isCurrentEra(post.era())) {                                     // BR-15
            throw new BusinessRuleException("HISTORY_READ_ONLY", "Posts from previous eras are read-only.");
        }
    }

    /** IDs only: each feed reloads what its viewer may see. */
    private void pushChange(Post post, String type) {
        realtime.toTopic(ForumTopics.channelPosts(post.channelId()), type, Map.of("postId", post.id()));
    }
}
