package pt.vclubroom.space.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pt.vclubroom.space.api.ChannelType;

class SpaceDomainTests {

    private static final Instant NOW = Instant.parse("2026-10-18T22:00:00Z");
    private final UUID club = UUID.randomUUID();
    private final UUID board = UUID.randomUUID();

    @Test
    void aPublicWriteRuleUnderAPrivateReadRuleIsNarrowedToTheReadRule() {          // R-23
        Space lounge = Space.create(club, "Members lounge", null, 1, AccessRule.clubMembers(), AccessRule.publicRule(), NOW);
        assertThat(lounge.writeRule().level()).isEqualTo(AccessLevel.CLUB_MEMBERS);

        Space boardRoom = Space.create(club, "Board room", null, 2, AccessRule.groups(List.of(board)), AccessRule.publicRule(), NOW);
        assertThat(boardRoom.writeRule().level()).isEqualTo(AccessLevel.GROUPS);
        assertThat(boardRoom.writeRule().groupIds()).containsExactly(board);
    }

    @Test
    void narrowerWriteRulesAreKept() {
        Space announcements = Space.create(club, "Announcements", null, 1, AccessRule.publicRule(), AccessRule.groups(List.of(board)), NOW);
        assertThat(announcements.readRule().level()).isEqualTo(AccessLevel.PUBLIC);
        assertThat(announcements.writeRule().level()).isEqualTo(AccessLevel.GROUPS);
    }

    @Test
    void onlyGroupRulesKeepGroupIds() {
        assertThat(AccessRule.of(AccessLevel.CLUB_MEMBERS, List.of(board)).groupIds()).isEmpty();
        assertThat(AccessRule.groups(List.of(board)).withoutGroup(board).groupIds()).isEmpty();   // empty GROUPS rule: fails closed
    }

    @Test
    void channelsGetASlugAndThePositionAfterTheLastOne() {
        Space general = Space.create(club, "General Chat & News!", null, 0, AccessRule.publicRule(), AccessRule.clubMembers(), NOW);
        Channel posts = general.addChannel(ChannelType.FORUM, "Posts", NOW);
        Channel lounge = general.addChannel(ChannelType.CHAT, "The Lounge", NOW);

        assertThat(general.slug()).isEqualTo("general-chat-news");
        assertThat(posts.spaceId()).isEqualTo(general.id());
        assertThat(lounge.slug()).isEqualTo("the-lounge");
        assertThat(lounge.position()).isEqualTo(1);
    }
}
