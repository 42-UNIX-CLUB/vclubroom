package pt.vclubroom.space.api;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import pt.vclubroom.shared.security.Actor;

/** Skeleton: the implementation (space.application) is not written yet. */
public interface SpaceAccessPolicy {

    /** @throws pt.vclubroom.shared.error.NotFoundException CHANNEL_NOT_FOUND */
    ChannelAccess forChannel(UUID channelId, Actor actor);

    /** @throws pt.vclubroom.shared.error.NotFoundException SPACE_NOT_FOUND */
    SpaceAccess forSpace(UUID spaceId, Actor actor);

    /** Batch read filter (campus calendar, club feed): returns the subset the actor can read. */
    Set<UUID> filterReadableSpaces(Actor actor, Collection<UUID> spaceIds);

    /** Forum channels of a club the actor can read (club feed). */
    Set<UUID> readableForumChannels(UUID clubId, Actor actor);
}
