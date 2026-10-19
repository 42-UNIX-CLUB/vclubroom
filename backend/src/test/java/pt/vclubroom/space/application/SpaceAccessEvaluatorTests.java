package pt.vclubroom.space.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pt.vclubroom.club.api.ClubCatalog;
import pt.vclubroom.club.api.ClubSnapshot;
import pt.vclubroom.club.api.ClubStatus;
import pt.vclubroom.club.api.JoinPolicy;
import pt.vclubroom.group.api.GroupMembershipQuery;
import pt.vclubroom.membership.api.ClubAuthorization;
import pt.vclubroom.membership.api.ClubPermission;
import pt.vclubroom.shared.security.Actor;
import pt.vclubroom.space.api.ChannelAccess;
import pt.vclubroom.space.api.ChannelType;
import pt.vclubroom.space.api.SpaceAccess;
import pt.vclubroom.space.domain.AccessRule;
import pt.vclubroom.space.domain.Channel;
import pt.vclubroom.space.domain.Space;
import pt.vclubroom.space.persistence.ChannelRepository;
import pt.vclubroom.space.persistence.SpaceRepository;

/** The access matrix of space.md §4.1, with every collaborator mocked (E05-S01-T3). */
class SpaceAccessEvaluatorTests {

    private static final Instant NOW = Instant.parse("2026-10-18T22:00:00Z");

    private final SpaceRepository spaces = mock(SpaceRepository.class);
    private final ChannelRepository channels = mock(ChannelRepository.class);
    private final ClubCatalog clubs = mock(ClubCatalog.class);
    private final ClubAuthorization authorization = mock(ClubAuthorization.class);
    private final GroupMembershipQuery groups = mock(GroupMembershipQuery.class);
    private final SpaceAccessEvaluator evaluator = new SpaceAccessEvaluator(spaces, channels, clubs, authorization, groups);

    private final UUID club = UUID.randomUUID();
    private final UUID board = UUID.randomUUID();
    private final Actor member = new Actor(UUID.randomUUID(), false);
    private final Actor stranger = new Actor(UUID.randomUUID(), false);
    private final Actor boardMember = new Actor(UUID.randomUUID(), false);
    private final Actor owner = new Actor(UUID.randomUUID(), false);
    private final Actor moderator = new Actor(UUID.randomUUID(), false);
    private final Actor admin = new Actor(UUID.randomUUID(), true);

    @BeforeEach
    void people() {
        clubIs(ClubStatus.ACTIVE);
        given(authorization.permissionsOf(any(), any())).willReturn(EnumSet.noneOf(ClubPermission.class));
        given(authorization.permissionsOf(club, owner)).willReturn(EnumSet.allOf(ClubPermission.class));
        given(authorization.permissionsOf(club, admin)).willReturn(EnumSet.allOf(ClubPermission.class));   // R-08
        given(authorization.permissionsOf(club, moderator)).willReturn(EnumSet.of(ClubPermission.CONTENT_MODERATE));
        for (Actor a : List.of(member, owner, moderator, boardMember)) {
            given(authorization.isActiveMember(club, a.userId())).willReturn(true);
        }
        given(groups.isMemberOfAny(boardMember.userId(), List.of(board))).willReturn(true);
    }

    private void clubIs(ClubStatus status) {
        given(clubs.require(club)).willReturn(new ClubSnapshot(club, "chess", "Chess Club", status, JoinPolicy.OPEN, 2));
    }

    private Space space(AccessRule read, AccessRule write) {
        Space space = Space.create(club, "Space " + UUID.randomUUID(), null, 0, read, write, NOW);
        given(spaces.findById(space.id())).willReturn(Optional.of(space));
        return space;
    }

    private SpaceAccess access(Space space, Actor actor) {
        return evaluator.forSpace(space.id(), actor);
    }

    @Test
    void publicSpacesAreReadByAnyLoggedInUser() {
        Space general = space(AccessRule.publicRule(), AccessRule.clubMembers());
        assertThat(access(general, stranger).canRead()).isTrue();
        assertThat(access(general, stranger).canWrite()).isFalse();
        assertThat(access(general, member).canWrite()).isTrue();
        assertThat(access(general, member).currentEra()).isEqualTo(2);
    }

    @Test
    void clubMembersSpacesAreForMembersOnly() {
        Space lounge = space(AccessRule.clubMembers(), AccessRule.clubMembers());
        assertThat(access(lounge, member).canRead()).isTrue();
        assertThat(access(lounge, member).canWrite()).isTrue();
        assertThat(access(lounge, stranger).canRead()).isFalse();
        assertThat(access(lounge, stranger).canWrite()).isFalse();
    }

    @Test
    void groupSpacesAreForTheGroupAndSpaceAccessAllAndAdminsBypass() {
        Space boardRoom = space(AccessRule.groups(List.of(board)), AccessRule.groups(List.of(board)));
        assertThat(access(boardRoom, boardMember).canWrite()).isTrue();
        assertThat(access(boardRoom, member).canRead()).isFalse();
        assertThat(access(boardRoom, owner).canRead()).isTrue();             // owners hold SPACE_ACCESS_ALL (D-29)
        assertThat(access(boardRoom, owner).canWrite()).isTrue();
        assertThat(access(boardRoom, admin).canModerate()).isTrue();
    }

    @Test
    void aGroupRuleWithoutGroupsFailsClosed() {
        given(groups.isMemberOfAny(any(), anyCollection())).willReturn(true);  // even if the group module said yes
        Space orphaned = space(AccessRule.groups(List.of()), AccessRule.groups(List.of()));
        assertThat(access(orphaned, boardMember).canRead()).isFalse();
        assertThat(access(orphaned, owner).canRead()).isTrue();              // owners can still fix the rule
    }

    @Test
    void nobodyWritesInAnArchivedClubOrAnArchivedSpace() {
        Space general = space(AccessRule.publicRule(), AccessRule.clubMembers());
        clubIs(ClubStatus.ARCHIVED);
        assertThat(access(general, member).canRead()).isTrue();
        assertThat(access(general, member).canWrite()).isFalse();
        assertThat(access(general, admin).canWrite()).isFalse();

        clubIs(ClubStatus.ACTIVE);
        general.archive(NOW);
        assertThat(access(general, owner).canWrite()).isFalse();
        assertThat(access(general, owner).canModerate()).isFalse();
    }

    @Test
    void channelsInheritTheSpaceRulesAndAnArchivedChannelIsReadOnly() {
        Space general = space(AccessRule.publicRule(), AccessRule.clubMembers());
        Channel posts = general.addChannel(ChannelType.FORUM, "Posts", NOW);
        given(channels.findById(posts.id())).willReturn(Optional.of(posts));

        ChannelAccess access = evaluator.forChannel(posts.id(), moderator);
        assertThat(access.type()).isEqualTo(ChannelType.FORUM);
        assertThat(access.canWrite()).isTrue();
        assertThat(access.canModerate()).isTrue();

        posts.archive(NOW);
        assertThat(evaluator.forChannel(posts.id(), moderator).canWrite()).isFalse();
        assertThat(evaluator.forChannel(posts.id(), moderator).canRead()).isTrue();
    }

    @Test
    void theNotificationAudienceOfAGroupSpaceIsGroupMembersPlusBypassHolders() {
        Space boardRoom = space(AccessRule.groups(List.of(board)), AccessRule.groups(List.of(board)));
        given(authorization.activeMemberIds(club)).willReturn(Set.of(member.userId(), boardMember.userId(), owner.userId()));
        given(groups.memberIdsOf(List.of(board))).willReturn(Set.of(boardMember.userId(), stranger.userId()));
        given(authorization.holdersOf(club, ClubPermission.SPACE_ACCESS_ALL)).willReturn(Set.of(owner.userId()));

        assertThat(evaluator.clubMembersWithReadAccess(boardRoom.id()))
            .containsExactlyInAnyOrder(boardMember.userId(), owner.userId());   // never non-members
    }
}
