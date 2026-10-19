package pt.vclubroom.forum.application;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import pt.vclubroom.forum.domain.Post;
import pt.vclubroom.forum.domain.ReactionTarget;
import pt.vclubroom.identity.api.UserDirectory;
import pt.vclubroom.identity.api.UserSummary;
import pt.vclubroom.media.api.MediaCatalog;
import pt.vclubroom.media.api.MediaView;
import pt.vclubroom.space.api.ChannelAccess;

/**
 * Posts → views in batches: one query for the authors, one for the media and two for the reactions, whatever the
 * page size (no N+1).
 */
@Component
class PostAssembler {

    private final UserDirectory users;
    private final MediaCatalog mediaCatalog;
    private final ReactionSummary reactions;

    PostAssembler(UserDirectory users, MediaCatalog mediaCatalog, ReactionSummary reactions) {
        this.users = users;
        this.mediaCatalog = mediaCatalog;
        this.reactions = reactions;
    }

    List<PostView> toViews(List<Post> posts, ChannelAccess access, UUID viewerId) {
        return toViews(posts, channelId -> access, viewerId);
    }

    /** Posts of several channels (the club feed): each one is judged with its own channel's access. */
    List<PostView> toViews(List<Post> posts, Function<UUID, ChannelAccess> accessOf, UUID viewerId) {
        Map<UUID, UserSummary> authors = users.findByIds(posts.stream().map(Post::authorId).distinct().toList());
        Collection<UUID> mediaIds = posts.stream().filter(p -> !p.isDeleted()).flatMap(p -> p.mediaIds().stream()).toList();
        Map<UUID, MediaView> media = mediaCatalog.resolve(mediaIds);
        Map<UUID, ReactionSummary.Reactions> reacted =
            reactions.of(ReactionTarget.POST, posts.stream().map(Post::id).toList(), viewerId);
        return posts.stream()
            .map(p -> view(p, accessOf.apply(p.channelId()), authors.get(p.authorId()), media,
                reacted.getOrDefault(p.id(), ReactionSummary.Reactions.NONE)))
            .toList();
    }

    private static PostView view(Post p, ChannelAccess access, UserSummary author, Map<UUID, MediaView> media,
                                 ReactionSummary.Reactions reacted) {
        boolean readOnly = !access.canWrite() || !access.isCurrentEra(p.era());       // BR-15
        List<MediaView> attached = p.isDeleted() ? List.of()
            : p.mediaIds().stream().map(media::get).filter(Objects::nonNull).toList();
        return new PostView(p.id(), p.channelId(), p.clubId(), p.era(), author, p.title(),
            p.isDeleted() ? null : p.body(), attached, p.commentCount(), reacted.counts(), reacted.mine(),
            p.isDeleted(), p.deletedByModerator(), readOnly, p.createdAt(), p.editedAt());
    }
}
