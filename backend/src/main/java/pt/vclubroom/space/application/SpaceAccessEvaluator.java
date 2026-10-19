package pt.vclubroom.space.application;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.club.api.ClubCatalog;
import pt.vclubroom.club.api.ClubSnapshot;
import pt.vclubroom.group.api.GroupMembershipQuery;
import pt.vclubroom.membership.api.ClubAuthorization;
import pt.vclubroom.membership.api.ClubPermission;
import pt.vclubroom.shared.error.NotFoundException;
import pt.vclubroom.shared.security.Actor;
import pt.vclubroom.space.api.ChannelAccess;
import pt.vclubroom.space.api.ChannelType;
import pt.vclubroom.space.api.SpaceAccess;
import pt.vclubroom.space.api.SpaceAccessPolicy;
import pt.vclubroom.space.api.SpaceAudience;
import pt.vclubroom.space.domain.AccessLevel;
import pt.vclubroom.space.domain.AccessRule;
import pt.vclubroom.space.domain.Channel;
import pt.vclubroom.space.domain.Space;
import pt.vclubroom.space.persistence.ChannelRepository;
import pt.vclubroom.space.persistence.SpaceRepository;

/** The one implementation of content access rules for the whole platform (space.md §4.1). */
@Service
@Transactional(readOnly = true)
class SpaceAccessEvaluator implements SpaceAccessPolicy, SpaceAudience {

    private final SpaceRepository spaces;
    private final ChannelRepository channels;
    private final ClubCatalog clubs;
    private final ClubAuthorization clubAuthorization;
    private final GroupMembershipQuery groupMembership;

    SpaceAccessEvaluator(SpaceRepository spaces, ChannelRepository channels, ClubCatalog clubs,
                         ClubAuthorization clubAuthorization, GroupMembershipQuery groupMembership) {
        this.spaces = spaces;
        this.channels = channels;
        this.clubs = clubs;
        this.clubAuthorization = clubAuthorization;
        this.groupMembership = groupMembership;
    }

    @Override
    public ChannelAccess forChannel(UUID channelId, Actor actor) {
        Channel channel = channels.findById(channelId)
            .orElseThrow(() -> new NotFoundException("CHANNEL_NOT_FOUND", "Channel not found"));
        Space space = spaces.findById(channel.spaceId())
            .orElseThrow(() -> new NotFoundException("SPACE_NOT_FOUND", "Space not found"));
        SpaceAccess s = evaluate(space, actor, clubs.require(space.clubId()));
        boolean writable = !channel.isArchived();
        return new ChannelAccess(channel.id(), channel.type(), space.id(), space.clubId(), s.currentEra(),
            s.canRead(), s.canWrite() && writable, s.canModerate() && writable);
    }

    @Override
    public SpaceAccess forSpace(UUID spaceId, Actor actor) {
        Space space = spaces.findById(spaceId)
            .orElseThrow(() -> new NotFoundException("SPACE_NOT_FOUND", "Space not found"));
        return evaluate(space, actor, clubs.require(space.clubId()));
    }

    @Override
    public Set<UUID> filterReadableSpaces(Actor actor, Collection<UUID> spaceIds) {
        if (spaceIds.isEmpty()) {
            return Set.of();
        }
        List<Space> found = spaces.findByIdIn(spaceIds);
        Map<UUID, ClubSnapshot> clubById = clubs.findByIds(found.stream().map(Space::clubId).collect(Collectors.toSet()));
        Set<UUID> readable = new HashSet<>();
        for (Space space : found) {
            ClubSnapshot club = clubById.get(space.clubId());
            if (club != null && evaluate(space, actor, club).canRead()) {
                readable.add(space.id());
            }
        }
        return readable;
    }

    @Override
    public Set<UUID> readableForumChannels(UUID clubId, Actor actor) {
        return readableChannels(clubId, actor, ChannelType.FORUM);
    }

    @Override
    public Set<UUID> readableChannels(UUID clubId, Actor actor, ChannelType type) {
        ClubSnapshot club = clubs.require(clubId);
        Set<UUID> result = new HashSet<>();
        for (Space space : spaces.findByClubIdOrderByPositionAsc(clubId)) {
            if (evaluate(space, actor, club).canRead()) {
                space.channels().stream()
                    .filter(c -> c.type() == type)
                    .forEach(c -> result.add(c.id()));
            }
        }
        return result;
    }

    @Override
    public Set<UUID> clubMembersWithReadAccess(UUID spaceId) {
        Space space = spaces.findById(spaceId)
            .orElseThrow(() -> new NotFoundException("SPACE_NOT_FOUND", "Space not found"));
        Set<UUID> members = clubAuthorization.activeMemberIds(space.clubId());
        if (space.readRule().level() != AccessLevel.GROUPS) {
            return members;                                            // PUBLIC or CLUB_MEMBERS → all members
        }
        Set<UUID> audience = new HashSet<>(groupMembership.memberIdsOf(space.readRule().groupIds()));
        audience.retainAll(members);
        audience.addAll(clubAuthorization.holdersOf(space.clubId(), ClubPermission.SPACE_ACCESS_ALL));
        return audience;
    }

    /** Package-private: SpaceQueryService evaluates every space of a club with one club lookup. */
    SpaceAccess evaluate(Space space, Actor actor, ClubSnapshot club) {
        Set<ClubPermission> permissions = clubAuthorization.permissionsOf(club.id(), actor);
        boolean bypass = permissions.contains(ClubPermission.SPACE_ACCESS_ALL);    // D-29; admins hold every permission
        boolean canRead = bypass || passes(space.readRule(), actor, club);
        boolean writable = club.isActive() && !space.isArchived();                // BR-02
        boolean canWrite = writable && (bypass || (canRead && passes(space.writeRule(), actor, club)));   // BR-11
        boolean canModerate = writable && canRead && permissions.contains(ClubPermission.CONTENT_MODERATE);
        return new SpaceAccess(space.id(), club.id(), club.currentEra(), canRead, canWrite, canModerate);
    }

    private boolean passes(AccessRule rule, Actor actor, ClubSnapshot club) {
        return switch (rule.level()) {
            case PUBLIC -> true;                                                  // any logged-in user
            case CLUB_MEMBERS -> clubAuthorization.isActiveMember(club.id(), actor.userId());
            // A GROUPS rule without groups fails closed here too, whatever the group module answers.
            case GROUPS -> !rule.groupIds().isEmpty() && groupMembership.isMemberOfAny(actor.userId(), rule.groupIds());
        };
    }
}
