package pt.vclubroom.space;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import pt.vclubroom.TestRequests;
import pt.vclubroom.TestcontainersConfiguration;
import pt.vclubroom.club.application.ClubService;
import pt.vclubroom.club.application.CreateClubCommand;
import pt.vclubroom.membership.api.ClubAuthorization;
import pt.vclubroom.shared.error.ForbiddenException;
import pt.vclubroom.shared.security.Actor;
import pt.vclubroom.space.api.ChannelAccess;
import pt.vclubroom.space.api.SpaceAccessPolicy;

/** Club, membership, group and space together: who may do what in a freshly approved club. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DefaultSpaceIntegrationTests {

    private final Actor founder = new Actor(UUID.randomUUID(), false);
    private final Actor stranger = new Actor(UUID.randomUUID(), false);

    @Autowired
    ClubService clubs;

    @Autowired
    ClubAuthorization authorization;

    @Autowired
    SpaceAccessPolicy access;

    @Test
    void inTheGeneralSpaceEveryoneReadsAndMembersPost() {
        UUID clubId = clubs.requestCreation(founder,
            new CreateClubCommand("Climbing " + TestRequests.suffix(), null, null, "Sports", List.of())).id();
        clubs.approve(new Actor(UUID.randomUUID(), true), clubId);
        await().atMost(Duration.ofSeconds(10)).until(() ->        // two listeners: membership and space
            authorization.isActiveMember(clubId, founder.userId()) && !access.readableForumChannels(clubId, founder).isEmpty());

        Set<UUID> forums = access.readableForumChannels(clubId, stranger);
        assertThat(forums).hasSize(1);                                    // "Posts"; "Lounge" is a chat
        UUID posts = forums.iterator().next();

        ChannelAccess founderAccess = access.forChannel(posts, founder);
        assertThat(founderAccess.canWrite()).isTrue();
        assertThat(founderAccess.canModerate()).isTrue();                 // the founder is Owner
        ChannelAccess strangerAccess = access.forChannel(posts, stranger);
        assertThat(strangerAccess.canRead()).isTrue();
        assertThatThrownBy(strangerAccess::requireWrite)
            .isInstanceOfSatisfying(ForbiddenException.class, e -> assertThat(e.code()).isEqualTo("CHANNEL_WRITE_DENIED"));
    }
}
