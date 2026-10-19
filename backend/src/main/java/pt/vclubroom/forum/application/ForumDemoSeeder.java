package pt.vclubroom.forum.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import pt.vclubroom.club.api.ClubCatalog;
import pt.vclubroom.forum.domain.ReactionTarget;
import pt.vclubroom.forum.persistence.PostRepository;
import pt.vclubroom.identity.api.UserDirectory;
import pt.vclubroom.shared.demo.DemoData;
import pt.vclubroom.shared.security.Actor;
import pt.vclubroom.space.api.SpaceAccessPolicy;

/** A few posts in each club's Posts channel, with a comment, a reply and reactions, written by its members. */
@Component
@Profile(DemoData.PROFILE)
@Order(DemoData.POSTS)
class ForumDemoSeeder implements ApplicationRunner {

    private record DemoPost(String club, String author, String title, String body, String commenter, String comment,
                            String replier, String reply, String reaction) { }

    private static final List<DemoPost> POSTS = List.of(
        new DemoPost("chess-club", "bob", "Friday blitz is back", "3+2, Cluster 2, from 19h. **Bring a clock** if you have one.",
            "carol", "I'll bring two!", "dave", "Count me in.", "party"),
        new DemoPost("chess-club", "carol", null, "Puzzle of the week: white to play and mate in 3. Answers in the thread.",
            "dave", "Qh7+ first?", null, null, "thumbs_up"),
        new DemoPost("42-robotics", "carol", "Competition team", "We are forming the team for the spring competition. Line followers this year!",
            null, null, null, null, "heart"),
        new DemoPost("photo-club", "dave", "This month's theme: reflections", "Puddles, windows, the river. Post your best by the 30th.",
            "bob", "The river at sunset, here I come.", null, null, "heart"),
        new DemoPost("hiking-club", "bob", "Sintra loop", "12 km, moderate. Train from Rossio at 8:41.",
            "carol", "Which exit at the station?", "bob", "The main one, we'll wait outside.", "thumbs_up"),
        new DemoPost("game-dev-club", "carol", "October jam", "Theme: *one button*. Teams of 2–3, demo night on the 31st.",
            "bob", "Looking for an artist!", null, null, "party"),
        new DemoPost(DemoData.FILM_SOCIETY, "dave", "First screening", "We open with *Cinema Paradiso*. Thursday, 20h, room 3.",
            "carol", "Bringing popcorn.", null, null, "heart"));

    private final PostService posts;
    private final CommentService comments;
    private final ReactionService reactions;
    private final PostRepository postRepository;
    private final SpaceAccessPolicy spaceAccess;
    private final ClubCatalog clubs;
    private final UserDirectory users;

    ForumDemoSeeder(PostService posts, CommentService comments, ReactionService reactions, PostRepository postRepository,
                    SpaceAccessPolicy spaceAccess, ClubCatalog clubs, UserDirectory users) {
        this.posts = posts;
        this.comments = comments;
        this.reactions = reactions;
        this.postRepository = postRepository;
        this.spaceAccess = spaceAccess;
        this.clubs = clubs;
        this.users = users;
    }

    /** The club's Posts channel, once the space module created it (ClubApproved). */
    static UUID postsChannel(SpaceAccessPolicy spaceAccess, ClubCatalog clubs, Actor admin, String slug) {
        UUID clubId = clubs.findBySlug(slug).orElseThrow().id();
        DemoData.awaitUntil(() -> !spaceAccess.readableForumChannels(clubId, admin).isEmpty(), slug + "'s Posts channel");
        return spaceAccess.readableForumChannels(clubId, admin).iterator().next();
    }

    @Override
    public void run(ApplicationArguments args) {
        Actor admin = actor("alice");
        for (String club : POSTS.stream().map(DemoPost::club).distinct().toList()) {
            UUID channelId = postsChannel(spaceAccess, clubs, admin, club);
            if (!postRepository.findPage(channelId, Instant.parse("9999-12-31T00:00:00Z"), new UUID(-1L, -1L), Limit.of(1)).isEmpty()) {
                continue;                                                    // seeded before
            }
            for (DemoPost demo : POSTS.stream().filter(p -> p.club().equals(club)).toList()) {
                PostView post = posts.createPost(actor(demo.author()), channelId, new CreatePostCommand(demo.title(), demo.body(), List.of()));
                if (demo.comment() != null) {
                    CommentView comment = comments.addComment(actor(demo.commenter()), post.id(), demo.comment(), null);
                    if (demo.reply() != null) {
                        comments.addComment(actor(demo.replier()), post.id(), demo.reply(), comment.id());
                    }
                }
                reactions.react(actor(demo.commenter() != null ? demo.commenter() : demo.author()), ReactionTarget.POST,
                    post.id(), demo.reaction(), true);
            }
        }
    }

    private Actor actor(String login) {
        return new Actor(users.findByLogin(login).orElseThrow().id(), "alice".equals(login));
    }
}
