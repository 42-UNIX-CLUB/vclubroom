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
import pt.vclubroom.club.api.ClubSnapshot;
import pt.vclubroom.club.api.ClubStatus;
import pt.vclubroom.forum.persistence.PostRepository;
import pt.vclubroom.identity.api.UserDirectory;
import pt.vclubroom.shared.demo.DemoData;
import pt.vclubroom.shared.security.Actor;
import pt.vclubroom.space.api.SpaceAccessPolicy;

/** Film Society's era 2 gets its own post, so the History tab has two eras to switch between. */
@Component
@Profile(DemoData.PROFILE)
@Order(DemoData.SECOND_ERA_POSTS)
class ForumDemoSecondEra implements ApplicationRunner {

    private final PostService posts;
    private final PostRepository postRepository;
    private final SpaceAccessPolicy spaceAccess;
    private final ClubCatalog clubs;
    private final UserDirectory users;

    ForumDemoSecondEra(PostService posts, PostRepository postRepository, SpaceAccessPolicy spaceAccess, ClubCatalog clubs,
                       UserDirectory users) {
        this.posts = posts;
        this.postRepository = postRepository;
        this.spaceAccess = spaceAccess;
        this.clubs = clubs;
        this.users = users;
    }

    @Override
    public void run(ApplicationArguments args) {
        ClubSnapshot film = clubs.findBySlug(DemoData.FILM_SOCIETY).orElseThrow();
        if (film.status() != ClubStatus.ACTIVE || film.currentEra() != 2) {
            return;
        }
        UUID channelId = ForumDemoSeeder.postsChannel(spaceAccess, clubs, new Actor(userId("alice"), true), DemoData.FILM_SOCIETY);
        if (postRepository.findPageInEra(channelId, 2, Instant.parse("9999-12-31T00:00:00Z"), new UUID(-1L, -1L), Limit.of(1)).isEmpty()) {
            posts.createPost(new Actor(userId("carol"), false), channelId, new CreatePostCommand("We're back",
                "A new promo picked up the projector. First film: *Amélie*.", List.of()));
        }
    }

    private UUID userId(String login) {
        return users.findByLogin(login).orElseThrow().id();
    }
}
