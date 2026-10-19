package pt.vclubroom.space.application;

import java.time.Clock;
import java.time.Instant;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import pt.vclubroom.club.events.ClubApproved;
import pt.vclubroom.space.api.ChannelType;
import pt.vclubroom.space.domain.AccessRule;
import pt.vclubroom.space.domain.Space;
import pt.vclubroom.space.persistence.SpaceRepository;

/** R-07: every approved club starts with a usable space. Idempotent (events are delivered at least once). */
@Component
class DefaultSpaceProvisioner {

    private final SpaceRepository spaces;
    private final Clock clock;

    DefaultSpaceProvisioner(SpaceRepository spaces, Clock clock) {
        this.spaces = spaces;
        this.clock = clock;
    }

    @ApplicationModuleListener
    void on(ClubApproved event) {
        if (spaces.existsByClubId(event.clubId())) {
            return;                                                        // already processed
        }
        Instant now = clock.instant();
        Space general = Space.create(event.clubId(), "General", "Everything about " + event.name(), 0,
            AccessRule.publicRule(), AccessRule.clubMembers(), now);
        general.addChannel(ChannelType.FORUM, "Posts", now);
        general.addChannel(ChannelType.CHAT, "Lounge", now);
        spaces.save(general);
    }
}
