package pt.vclubroom.forum.application;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.forum.domain.Comment;
import pt.vclubroom.forum.domain.Post;
import pt.vclubroom.forum.domain.ReactionTarget;
import pt.vclubroom.forum.events.CommentAdded;
import pt.vclubroom.forum.persistence.CommentRepository;
import pt.vclubroom.forum.persistence.PostRepository;
import pt.vclubroom.identity.api.UserDirectory;
import pt.vclubroom.identity.api.UserSummary;
import pt.vclubroom.realtime.api.RealtimePublisher;
import pt.vclubroom.shared.error.BusinessRuleException;
import pt.vclubroom.shared.error.NotFoundException;
import pt.vclubroom.shared.security.Actor;
import pt.vclubroom.shared.web.CursorPage;
import pt.vclubroom.shared.web.TimeCursor;
import pt.vclubroom.space.api.ChannelAccess;
import pt.vclubroom.space.api.SpaceAccessPolicy;

/**
 * Comments and replies (forum.md §4, BR-15, BR-17). Access is the post's channel's: read to see the thread, write
 * to take part, and only in the current era. New comments are pushed to the feed and the open thread; E09-S04 adds
 * edit and delete.
 */
@Service
public class CommentService {

    /** "Older than any comment": the first page starts here. */
    private static final TimeCursor FIRST_PAGE = new TimeCursor(Instant.EPOCH, new UUID(0L, 0L));

    private final PostRepository posts;
    private final CommentRepository comments;
    private final SpaceAccessPolicy spaceAccess;
    private final UserDirectory users;
    private final ReactionSummary reactions;
    private final ApplicationEventPublisher events;
    private final RealtimePublisher realtime;
    private final Clock clock;

    CommentService(PostRepository posts, CommentRepository comments, SpaceAccessPolicy spaceAccess,
                   UserDirectory users, ReactionSummary reactions, ApplicationEventPublisher events,
                   RealtimePublisher realtime, Clock clock) {
        this.posts = posts;
        this.comments = comments;
        this.spaceAccess = spaceAccess;
        this.users = users;
        this.reactions = reactions;
        this.events = events;
        this.realtime = realtime;
        this.clock = clock;
    }

    @Transactional
    public CommentView addComment(Actor actor, UUID postId, String body, UUID parentId) {
        Post post = posts.findForUpdate(postId)
            .filter(p -> !p.isDeleted())
            .orElseThrow(() -> new NotFoundException("POST_NOT_FOUND", "Post not found"));
        ChannelAccess access = spaceAccess.forChannel(post.channelId(), actor).requireWrite();   // CHANNEL_WRITE_DENIED
        if (!access.isCurrentEra(post.era())) {                                                 // BR-15
            throw new BusinessRuleException("HISTORY_READ_ONLY", "Posts from previous eras are read-only.");
        }
        Comment parent = parentId == null ? null : comments.findById(parentId)
            .orElseThrow(() -> new BusinessRuleException("INVALID_PARENT", "The comment you reply to does not exist."));
        Instant now = clock.instant();
        Comment comment = Comment.reply(post, parent, actor.userId(), body, now);                // BR-17
        comments.save(comment);
        post.commentAdded(now);

        events.publishEvent(new CommentAdded(comment.id(), postId, comment.parentId(), post.clubId(), post.spaceId(),
            actor.userId(), post.authorId(), parent == null ? null : parent.authorId(), comment.excerpt(), now));
        Map<String, Object> pushed = new HashMap<>();
        pushed.put("postId", postId);
        pushed.put("commentId", comment.id());
        pushed.put("parentId", comment.parentId());                       // null for a top-level comment
        pushed.put("commentCount", post.commentCount());                   // a value, not "+1": repeats are harmless
        realtime.toTopic(ForumTopics.channelPosts(post.channelId()), "comment.created", pushed);   // the feed's counter
        realtime.toTopic(ForumTopics.post(postId), "comment.created", pushed);                     // the open thread
        return toViews(List.of(comment), actor.userId()).getFirst();
    }

    /** Oldest first, replies by time among the comments; the SPA groups them under their parents. */
    @Transactional(readOnly = true)
    public CursorPage<CommentView> list(Actor actor, UUID postId, String cursor, int limit) {
        Post post = posts.findById(postId)
            .orElseThrow(() -> new NotFoundException("POST_NOT_FOUND", "Post not found"));
        spaceAccess.forChannel(post.channelId(), actor).requireRead();
        TimeCursor after = cursor == null ? FIRST_PAGE : TimeCursor.decode(cursor);
        List<Comment> rows = comments.findPage(postId, after.createdAt(), after.id(), Limit.of(limit + 1));
        List<Comment> page = rows.size() > limit ? rows.subList(0, limit) : rows;
        String next = rows.size() > limit ? new TimeCursor(page.getLast().createdAt(), page.getLast().id()).encode() : null;
        return new CursorPage<>(toViews(page, actor.userId()), next);
    }

    private List<CommentView> toViews(List<Comment> page, UUID viewerId) {
        Map<UUID, UserSummary> authors = users.findByIds(page.stream().map(Comment::authorId).distinct().toList());
        Map<UUID, ReactionSummary.Reactions> reacted =
            reactions.of(ReactionTarget.COMMENT, page.stream().map(Comment::id).toList(), viewerId);
        return page.stream()
            .map(c -> {
                ReactionSummary.Reactions r = reacted.getOrDefault(c.id(), ReactionSummary.Reactions.NONE);
                return new CommentView(c.id(), c.postId(), c.parentId(), authors.get(c.authorId()),
                    c.isDeleted() ? null : c.body(), r.counts(), r.mine(), c.isDeleted(), c.deletedByModerator(),
                    c.createdAt(), c.editedAt());
            })
            .toList();
    }
}
