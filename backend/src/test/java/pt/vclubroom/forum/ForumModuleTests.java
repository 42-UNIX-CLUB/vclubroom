package pt.vclubroom.forum;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.Scenario;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pt.vclubroom.TestcontainersConfiguration;
import pt.vclubroom.forum.application.CommentService;
import pt.vclubroom.forum.application.CommentView;
import pt.vclubroom.forum.application.CreatePostCommand;
import pt.vclubroom.forum.application.PostService;
import pt.vclubroom.forum.application.PostView;
import pt.vclubroom.forum.events.CommentAdded;
import pt.vclubroom.forum.events.PostPublished;
import pt.vclubroom.identity.api.UserDirectory;
import pt.vclubroom.media.api.MediaCatalog;
import pt.vclubroom.realtime.api.RealtimePublisher;
import pt.vclubroom.shared.error.ForbiddenException;
import pt.vclubroom.shared.security.Actor;
import pt.vclubroom.space.api.ChannelAccess;
import pt.vclubroom.space.api.ChannelType;
import pt.vclubroom.space.api.SpaceAccessPolicy;

/** The forum module on its own (E09-S01, E09-S02): the space, media, identity and realtime APIs are mocked. */
@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
class ForumModuleTests {

    @MockitoBean
    SpaceAccessPolicy spaceAccess;

    @MockitoBean
    MediaCatalog mediaCatalog;

    @MockitoBean
    UserDirectory users;

    @MockitoBean
    RealtimePublisher realtime;

    @Autowired
    PostService posts;

    @Autowired
    CommentService comments;

    private ChannelAccess access(UUID channelId, boolean canWrite) {
        return new ChannelAccess(channelId, ChannelType.FORUM, UUID.randomUUID(), UUID.randomUUID(), 2, true, canWrite, false);
    }

    @Test
    void publishingAnnouncesThePostStampedWithTheCurrentEra(Scenario scenario) {
        UUID channelId = UUID.randomUUID();
        given(spaceAccess.forChannel(any(), any())).willReturn(access(channelId, true));
        given(users.findByIds(anyCollection())).willReturn(Map.of());
        given(mediaCatalog.resolve(anyCollection())).willReturn(Map.of());

        scenario.stimulate(() -> posts.createPost(new Actor(UUID.randomUUID(), false), channelId,
                new CreatePostCommand("Friday", "Tournament on Friday!", List.of())))
            .andWaitForEventOfType(PostPublished.class)
            .matching(e -> e.channelId().equals(channelId))
            .toArriveAndVerify(e -> {
                assertThat(e.era()).isEqualTo(2);
                assertThat(e.title()).isEqualTo("Friday");
                assertThat(e.excerpt()).isEqualTo("Tournament on Friday!");
            });
    }

    @Test
    void readersCannotPost() {
        UUID channelId = UUID.randomUUID();
        given(spaceAccess.forChannel(any(), any())).willReturn(access(channelId, false));

        assertThatThrownBy(() -> posts.createPost(new Actor(UUID.randomUUID(), false), channelId,
                new CreatePostCommand(null, "Hi", List.of())))
            .isInstanceOfSatisfying(ForbiddenException.class, e -> assertThat(e.code()).isEqualTo("CHANNEL_WRITE_DENIED"));
    }

    @Test
    void aReplyTellsWhoWroteThePostAndTheParentComment(Scenario scenario) {
        UUID channelId = UUID.randomUUID();
        given(spaceAccess.forChannel(any(), any())).willReturn(access(channelId, true));
        given(users.findByIds(anyCollection())).willReturn(Map.of());
        given(mediaCatalog.resolve(anyCollection())).willReturn(Map.of());
        UUID postAuthor = UUID.randomUUID();
        UUID commenter = UUID.randomUUID();
        PostView post = posts.createPost(new Actor(postAuthor, false), channelId, new CreatePostCommand(null, "Hello", List.of()));
        CommentView top = comments.addComment(new Actor(commenter, false), post.id(), "Nice!", null);

        scenario.stimulate(() -> comments.addComment(new Actor(UUID.randomUUID(), false), post.id(), "Agreed", top.id()))
            .andWaitForEventOfType(CommentAdded.class)
            .matching(e -> top.id().equals(e.parentId()))
            .toArriveAndVerify(e -> {
                assertThat(e.postAuthorId()).isEqualTo(postAuthor);
                assertThat(e.parentAuthorId()).isEqualTo(commenter);
                assertThat(e.excerpt()).isEqualTo("Agreed");
            });
    }
}
