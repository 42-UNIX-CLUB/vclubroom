package pt.vclubroom.space.web;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pt.vclubroom.shared.security.AuthenticatedUser;
import pt.vclubroom.space.application.ChannelView;
import pt.vclubroom.space.application.SpaceQueryService;
import pt.vclubroom.space.application.SpaceService;
import pt.vclubroom.space.web.dto.UpdateChannelRequest;

/** One channel: the forum and chat views start here; SPACE_MANAGE renames, moves and archives it. */
@RestController
class ChannelController {

    private final SpaceQueryService queries;
    private final SpaceService spaces;

    ChannelController(SpaceQueryService queries, SpaceService spaces) {
        this.queries = queries;
        this.spaces = spaces;
    }

    @GetMapping("/api/channels/{channelId}")
    ChannelView channel(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("channelId") UUID channelId) {
        return queries.channel(channelId, me.actor());
    }

    @PatchMapping("/api/channels/{channelId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void update(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("channelId") UUID channelId,
                @Valid @RequestBody UpdateChannelRequest request) {
        if (request.name() != null) {
            spaces.renameChannel(me.actor(), channelId, request.name());
        }
        if (request.position() != null) {
            spaces.moveChannel(me.actor(), channelId, request.position());
        }
    }

    @PostMapping("/api/channels/{channelId}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void archive(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("channelId") UUID channelId) {
        spaces.archiveChannel(me.actor(), channelId);
    }
}
