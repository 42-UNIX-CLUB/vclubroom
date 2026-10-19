package pt.vclubroom.forum.application;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;
import pt.vclubroom.forum.domain.Post;
import pt.vclubroom.identity.api.UserDirectory;
import pt.vclubroom.identity.api.UserSummary;
import pt.vclubroom.media.api.MediaCatalog;
import pt.vclubroom.media.api.MediaView;
import pt.vclubroom.space.api.ChannelAccess;

/** Posts → views in batches: one query for the authors and one for the media, whatever the page size (no N+1). */
@Component
class PostAssembler {

    private final UserDirectory users;
    private final MediaCatalog mediaCatalog;

    PostAssembler(UserDirectory users, MediaCatalog mediaCatalog) {
        this.users = users;
        this.mediaCatalog = mediaCatalog;
    }

    List<PostView> toViews(List<Post> posts, ChannelAccess access) {
        Map<UUID, UserSummary> authors = users.findByIds(posts.stream().map(Post::authorId).distinct().toList());
        Collection<UUID> mediaIds = posts.stream().filter(p -> !p.isDeleted()).flatMap(p -> p.mediaIds().stream()).toList();
        Map<UUID, MediaView> media = mediaCatalog.resolve(mediaIds);
        return posts.stream().map(p -> view(p, access, authors.get(p.authorId()), media)).toList();
    }

    private static PostView view(Post p, ChannelAccess access, UserSummary author, Map<UUID, MediaView> media) {
        boolean readOnly = !access.canWrite() || !access.isCurrentEra(p.era());       // BR-15
        List<MediaView> attached = p.isDeleted() ? List.of()
            : p.mediaIds().stream().map(media::get).filter(Objects::nonNull).toList();
        return new PostView(p.id(), p.channelId(), p.clubId(), p.era(), author, p.title(),
            p.isDeleted() ? null : p.body(), attached, p.commentCount(), p.isDeleted(), p.deletedByModerator(),
            readOnly, p.createdAt(), p.editedAt());
    }
}
