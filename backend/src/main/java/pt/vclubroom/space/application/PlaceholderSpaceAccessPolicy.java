package pt.vclubroom.space.application;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import pt.vclubroom.shared.error.NotFoundException;
import pt.vclubroom.shared.security.Actor;
import pt.vclubroom.space.api.ChannelAccess;
import pt.vclubroom.space.api.SpaceAccess;
import pt.vclubroom.space.api.SpaceAccessPolicy;

/**
 * Temporary bean so the application starts before the space module exists. It fails closed: no space or
 * channel exists and nothing is readable. Delete it when E05-S01 adds the real SpaceAccessPolicy.
 */
@Service
class PlaceholderSpaceAccessPolicy implements SpaceAccessPolicy {

    @Override
    public ChannelAccess forChannel(UUID channelId, Actor actor) {
        throw new NotFoundException("CHANNEL_NOT_FOUND", "Channel not found");
    }

    @Override
    public SpaceAccess forSpace(UUID spaceId, Actor actor) {
        throw new NotFoundException("SPACE_NOT_FOUND", "Space not found");
    }

    @Override
    public Set<UUID> filterReadableSpaces(Actor actor, Collection<UUID> spaceIds) {
        return Set.of();
    }

    @Override
    public Set<UUID> readableForumChannels(UUID clubId, Actor actor) {
        return Set.of();
    }
}
