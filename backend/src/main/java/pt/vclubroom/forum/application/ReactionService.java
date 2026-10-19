package pt.vclubroom.forum.application;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.forum.domain.Comment;
import pt.vclubroom.forum.domain.Post;
import pt.vclubroom.forum.domain.ReactionTarget;
import pt.vclubroom.forum.persistence.CommentRepository;
import pt.vclubroom.forum.persistence.PostRepository;
import pt.vclubroom.forum.persistence.ReactionRepository;
import pt.vclubroom.shared.error.BusinessRuleException;
import pt.vclubroom.shared.error.InvalidRequestException;
import pt.vclubroom.shared.error.NotFoundException;
import pt.vclubroom.shared.security.Actor;
import pt.vclubroom.space.api.ChannelAccess;
import pt.vclubroom.space.api.SpaceAccessPolicy;

/**
 * Adding and removing reactions (forum.md §8.3). Both are idempotent, so the SPA can toggle optimistically and
 * simply retry. Same access as commenting: write access in the post's channel, current era only.
 * E10-S01 pushes reactions.changed to /topic/posts/{postId}.
 */
@Service
public class ReactionService {

    private final PostRepository posts;
    private final CommentRepository comments;
    private final ReactionRepository reactions;
    private final ReactionSummary summary;
    private final SpaceAccessPolicy spaceAccess;
    private final ForumProperties properties;

    ReactionService(PostRepository posts, CommentRepository comments, ReactionRepository reactions,
                    ReactionSummary summary, SpaceAccessPolicy spaceAccess, ForumProperties properties) {
        this.posts = posts;
        this.comments = comments;
        this.reactions = reactions;
        this.summary = summary;
        this.spaceAccess = spaceAccess;
        this.properties = properties;
    }

    /** The reactions of the target afterwards, as the viewer sees them. */
    public record ReactionState(Map<String, Long> counts, Set<String> mine) { }

    @Transactional
    public ReactionState react(Actor actor, ReactionTarget target, UUID targetId, String code, boolean add) {
        if (!properties.isAllowedReaction(code)) {                                          // BR-18
            throw new InvalidRequestException("UNKNOWN_REACTION", "Unknown reaction " + code);
        }
        Post post = postOf(target, targetId);
        ChannelAccess access = spaceAccess.forChannel(post.channelId(), actor).requireWrite();   // CHANNEL_WRITE_DENIED
        if (!access.isCurrentEra(post.era())) {                                             // BR-15
            throw new BusinessRuleException("HISTORY_READ_ONLY", "Posts from previous eras are read-only.");
        }
        if (add) {
            reactions.addIfAbsent(target, targetId, actor.userId(), code);
        } else {
            reactions.remove(target, targetId, actor.userId(), code);
        }
        ReactionSummary.Reactions now = summary.of(target, targetId, actor.userId());
        return new ReactionState(now.counts(), now.mine());
    }

    private Post postOf(ReactionTarget target, UUID targetId) {
        UUID postId = switch (target) {
            case POST -> targetId;
            case COMMENT -> comments.findById(targetId)
                .filter(c -> !c.isDeleted())
                .map(Comment::postId)
                .orElseThrow(() -> new NotFoundException("COMMENT_NOT_FOUND", "Comment not found"));
        };
        return posts.findById(postId)
            .filter(p -> !p.isDeleted())
            .orElseThrow(() -> new NotFoundException("POST_NOT_FOUND", "Post not found"));
    }
}
