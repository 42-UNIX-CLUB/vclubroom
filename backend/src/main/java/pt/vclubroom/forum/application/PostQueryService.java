package pt.vclubroom.forum.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.forum.domain.Post;
import pt.vclubroom.forum.persistence.PostRepository;
import pt.vclubroom.shared.error.NotFoundException;
import pt.vclubroom.shared.security.Actor;
import pt.vclubroom.shared.web.CursorPage;
import pt.vclubroom.shared.web.TimeCursor;
import pt.vclubroom.space.api.ChannelAccess;
import pt.vclubroom.space.api.ChannelType;
import pt.vclubroom.space.api.SpaceAccessPolicy;

/** Reading a channel (forum.md §8.2): one keyset query, then batch decoration. */
@Service
@Transactional(readOnly = true)
public class PostQueryService {

    /** "Newer than any post": the first page starts here. PostgreSQL cannot store Instant.MAX, hence a far date. */
    private static final TimeCursor FIRST_PAGE = new TimeCursor(Instant.parse("9999-12-31T23:59:59Z"), new UUID(-1L, -1L));

    private final PostRepository posts;
    private final SpaceAccessPolicy spaceAccess;
    private final PostAssembler assembler;

    PostQueryService(PostRepository posts, SpaceAccessPolicy spaceAccess, PostAssembler assembler) {
        this.posts = posts;
        this.spaceAccess = spaceAccess;
        this.assembler = assembler;
    }

    /** One post, for whoever can read its channel (a notification's link lands here, E12-S01). */
    public PostView get(Actor actor, UUID postId) {
        Post post = posts.findById(postId).orElseThrow(() -> new NotFoundException("POST_NOT_FOUND", "Post not found"));
        ChannelAccess access = spaceAccess.forChannel(post.channelId(), actor).requireRead();
        return assembler.toViews(List.of(post), access, actor.userId()).getFirst();
    }

    /** Newest first; era = null for every era, else only that era (the History tab, E14-S03). */
    public CursorPage<PostView> list(Actor actor, UUID channelId, Integer era, String cursor, int limit) {
        ChannelAccess access = spaceAccess.forChannel(channelId, actor).requireType(ChannelType.FORUM).requireRead();
        TimeCursor after = cursor == null ? FIRST_PAGE : TimeCursor.decode(cursor);
        Limit oneMore = Limit.of(limit + 1);                                   // the extra row says "there is more"
        List<Post> rows = era == null
            ? posts.findPage(channelId, after.createdAt(), after.id(), oneMore)
            : posts.findPageInEra(channelId, era, after.createdAt(), after.id(), oneMore);
        List<Post> page = rows.size() > limit ? rows.subList(0, limit) : rows;
        String next = rows.size() > limit ? new TimeCursor(page.getLast().createdAt(), page.getLast().id()).encode() : null;
        return new CursorPage<>(assembler.toViews(page, access, actor.userId()), next);
    }
}
