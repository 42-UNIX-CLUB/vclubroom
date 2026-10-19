package pt.vclubroom.space.web;

import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import pt.vclubroom.shared.security.AuthenticatedUser;
import pt.vclubroom.space.application.ChannelView;
import pt.vclubroom.space.application.SpaceQueryService;

/** Reading a channel: the forum and chat views start here. E05-S03 adds renaming and archiving. */
@RestController
class ChannelController {

    private final SpaceQueryService queries;

    ChannelController(SpaceQueryService queries) {
        this.queries = queries;
    }

    @GetMapping("/api/channels/{channelId}")
    ChannelView channel(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("channelId") UUID channelId) {
        return queries.channel(channelId, me.actor());
    }
}
