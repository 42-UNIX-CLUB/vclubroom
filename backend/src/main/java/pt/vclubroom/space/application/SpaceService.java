package pt.vclubroom.space.application;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.group.api.GroupCatalog;
import pt.vclubroom.membership.api.ClubAuthorization;
import pt.vclubroom.membership.api.ClubPermission;
import pt.vclubroom.shared.error.ConflictException;
import pt.vclubroom.shared.error.InvalidRequestException;
import pt.vclubroom.shared.error.NotFoundException;
import pt.vclubroom.shared.security.Actor;
import pt.vclubroom.space.api.ChannelType;
import pt.vclubroom.space.domain.AccessLevel;
import pt.vclubroom.space.domain.AccessRule;
import pt.vclubroom.space.domain.Channel;
import pt.vclubroom.space.domain.Space;
import pt.vclubroom.space.persistence.ChannelRepository;
import pt.vclubroom.space.persistence.SpaceRepository;

/**
 * Managing spaces and channels (space.md §8.3): SPACE_MANAGE in an active club for every change. Rules may name this
 * club's groups or campus groups only (BR-12), and a GROUPS rule must name at least one. Nothing is deleted: spaces
 * and channels are archived, which makes them read-only and hidden by default. Slugs never change (they are in URLs).
 */
@Service
public class SpaceService {

    /** What a rule looks like on the way in: GROUPS needs groupIds, the other levels ignore them. */
    public record Rule(AccessLevel level, List<UUID> groupIds) { }

    private final SpaceRepository spaces;
    private final ChannelRepository channels;
    private final ClubAuthorization authorization;
    private final GroupCatalog groups;
    private final Clock clock;

    SpaceService(SpaceRepository spaces, ChannelRepository channels, ClubAuthorization authorization, GroupCatalog groups,
                 Clock clock) {
        this.spaces = spaces;
        this.channels = channels;
        this.authorization = authorization;
        this.groups = groups;
        this.clock = clock;
    }

    /** At the end of the list. R-23: a public write rule under a non-public read rule becomes the read rule. */
    @Transactional
    public Space create(Actor actor, UUID clubId, String name, String description, Rule read, Rule write) {
        authorization.require(clubId, actor, ClubPermission.SPACE_MANAGE);
        List<Space> existing = spaces.findByClubIdOrderByPositionAsc(clubId);
        String slug = requireSlug(name);
        if (existing.stream().anyMatch(s -> s.slug().equals(slug))) {
            throw new ConflictException("SPACE_NAME_TAKEN", "A space with this name already exists.");
        }
        int position = existing.stream().mapToInt(Space::position).max().orElse(-1) + 1;
        return spaces.save(Space.create(clubId, name.trim(), blankToNull(description), position,
            rule(clubId, read), rule(clubId, write), clock.instant()));
    }

    /** Whatever is present changes; read and write go together (the R-23 normalisation needs both). */
    @Transactional
    public void update(Actor actor, UUID spaceId, String name, String description, Rule read, Rule write) {
        Space space = manageable(actor, spaceId);
        space.describe(name == null ? null : name.trim(), description);
        if (read != null || write != null) {
            space.changeAccess(read == null ? space.readRule() : rule(space.clubId(), read),
                write == null ? space.writeRule() : rule(space.clubId(), write));
        }
    }

    /** Puts the space at that place in the club's list (0 = first); the others shift. */
    @Transactional
    public void move(Actor actor, UUID spaceId, int position) {
        Space space = manageable(actor, spaceId);
        List<Space> ordered = new ArrayList<>(spaces.findByClubIdOrderByPositionAsc(space.clubId()));
        ordered.remove(space);
        ordered.add(Math.clamp(position, 0, ordered.size()), space);
        for (int i = 0; i < ordered.size(); i++) {
            ordered.get(i).moveTo(i);
        }
    }

    @Transactional
    public void archive(Actor actor, UUID spaceId) {
        manageable(actor, spaceId).archive(clock.instant());
    }

    @Transactional
    public Channel addChannel(Actor actor, UUID spaceId, ChannelType type, String name) {
        Space space = manageable(actor, spaceId);
        String slug = requireSlug(name);
        if (space.channels().stream().anyMatch(c -> c.slug().equals(slug))) {
            throw new ConflictException("CHANNEL_NAME_TAKEN", "A channel with this name already exists in this space.");
        }
        return space.addChannel(type, name.trim(), clock.instant());
    }

    @Transactional
    public void renameChannel(Actor actor, UUID channelId, String name) {
        Channel channel = loadChannel(channelId);
        manageable(actor, channel.spaceId());
        channel.rename(name.trim());
    }

    /** Puts the channel at that place in its space (0 = first). */
    @Transactional
    public void moveChannel(Actor actor, UUID channelId, int position) {
        Channel channel = loadChannel(channelId);
        Space space = manageable(actor, channel.spaceId());
        List<Channel> ordered = new ArrayList<>(space.channels());
        ordered.remove(channel);
        ordered.add(Math.clamp(position, 0, ordered.size()), channel);
        for (int i = 0; i < ordered.size(); i++) {
            ordered.get(i).moveTo(i);
        }
    }

    @Transactional
    public void archiveChannel(Actor actor, UUID channelId) {
        Channel channel = loadChannel(channelId);
        manageable(actor, channel.spaceId());
        channel.archive(clock.instant());
    }

    private Space manageable(Actor actor, UUID spaceId) {
        Space space = spaces.findById(spaceId).orElseThrow(() -> new NotFoundException("SPACE_NOT_FOUND", "Space not found"));
        authorization.require(space.clubId(), actor, ClubPermission.SPACE_MANAGE);    // CLUB_NOT_ACTIVE / PERMISSION_DENIED
        return space;
    }

    private Channel loadChannel(UUID channelId) {
        return channels.findById(channelId).orElseThrow(() -> new NotFoundException("CHANNEL_NOT_FOUND", "Channel not found"));
    }

    private AccessRule rule(UUID clubId, Rule rule) {
        if (rule.level() == AccessLevel.GROUPS) {
            Set<UUID> ids = new HashSet<>(rule.groupIds() == null ? List.of() : rule.groupIds());
            if (ids.isEmpty()) {
                throw new InvalidRequestException("EMPTY_GROUP_RULE", "Choose at least one group.");
            }
            groups.assertUsableInClub(clubId, ids);                                     // BR-12: GROUP_NOT_USABLE
            return AccessRule.groups(List.copyOf(ids));
        }
        return AccessRule.of(rule.level(), List.of());
    }

    private static String requireSlug(String name) {
        String slug = Space.slugOf(name.trim());
        if (slug.isEmpty()) {
            throw new InvalidRequestException("NAME_NEEDS_LETTERS", "Use at least one letter or digit in the name.");
        }
        return slug;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
