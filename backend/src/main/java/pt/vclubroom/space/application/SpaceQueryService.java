package pt.vclubroom.space.application;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.club.api.ClubCatalog;
import pt.vclubroom.club.api.ClubSnapshot;
import pt.vclubroom.shared.error.NotFoundException;
import pt.vclubroom.shared.security.Actor;
import pt.vclubroom.space.api.SpaceAccess;
import pt.vclubroom.space.domain.Channel;
import pt.vclubroom.space.domain.Space;
import pt.vclubroom.space.persistence.ChannelRepository;
import pt.vclubroom.space.persistence.SpaceRepository;

/** Read side of spaces: only what the viewer can read, with their flags. SpaceService changes them. */
@Service
@Transactional(readOnly = true)
public class SpaceQueryService {

    private final SpaceRepository spaces;
    private final ChannelRepository channels;
    private final ClubCatalog clubs;
    private final SpaceAccessEvaluator access;

    SpaceQueryService(SpaceRepository spaces, ChannelRepository channels, ClubCatalog clubs, SpaceAccessEvaluator access) {
        this.spaces = spaces;
        this.channels = channels;
        this.clubs = clubs;
        this.access = access;
    }

    /** The club sidebar: readable spaces in order; archived spaces and channels only on request. */
    public List<SpaceView> spacesOf(UUID clubId, Actor actor, boolean includeArchived) {
        ClubSnapshot club = clubs.require(clubId);
        return spaces.findByClubIdOrderByPositionAsc(clubId).stream()
            .filter(space -> includeArchived || !space.isArchived())
            .map(space -> view(space, access.evaluate(space, actor, club), includeArchived))
            .filter(view -> view != null)
            .toList();
    }

    /** @throws pt.vclubroom.shared.error.ForbiddenException SPACE_READ_DENIED */
    public SpaceView space(UUID spaceId, Actor actor) {
        SpaceAccess spaceAccess = access.forSpace(spaceId, actor).requireRead();
        Space space = spaces.findById(spaceId).orElseThrow();
        return view(space, spaceAccess, true);
    }

    /** @throws pt.vclubroom.shared.error.ForbiddenException CHANNEL_READ_DENIED */
    public ChannelView channel(UUID channelId, Actor actor) {
        var channelAccess = access.forChannel(channelId, actor).requireRead();
        Channel channel = channels.findById(channelId).orElseThrow();
        Space space = spaces.findById(channel.spaceId())
            .orElseThrow(() -> new NotFoundException("SPACE_NOT_FOUND", "Space not found"));
        return new ChannelView(channel.id(), space.id(), space.slug(), space.name(), channel.type(), channel.name(),
            channel.slug(), channel.position(), channel.isArchived(), channelAccess.canWrite(),
            channelAccess.canModerate(), channelAccess.currentEra());
    }

    /** null when the viewer cannot read the space. Channels inherit the space's flags; archived ones are read-only. */
    private static SpaceView view(Space space, SpaceAccess a, boolean includeArchived) {
        if (!a.canRead()) {
            return null;
        }
        List<ChannelView> channelViews = space.channels().stream()
            .filter(c -> includeArchived || !c.isArchived())
            .map(c -> new ChannelView(c.id(), space.id(), space.slug(), space.name(), c.type(), c.name(), c.slug(),
                c.position(), c.isArchived(), a.canWrite() && !c.isArchived(), a.canModerate() && !c.isArchived(),
                a.currentEra()))
            .toList();
        return new SpaceView(space.id(), space.name(), space.slug(), space.description(), space.position(),
            space.readRule().level(), space.readRule().groupIds(), space.writeRule().level(), space.writeRule().groupIds(),
            space.isArchived(), a.canWrite(), a.canModerate(), channelViews);
    }
}
