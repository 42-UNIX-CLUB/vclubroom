package pt.vclubroom.space.web;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pt.vclubroom.shared.security.AuthenticatedUser;
import pt.vclubroom.space.application.SpaceQueryService;
import pt.vclubroom.space.application.SpaceService;
import pt.vclubroom.space.application.SpaceView;
import pt.vclubroom.space.domain.Channel;
import pt.vclubroom.space.domain.Space;
import pt.vclubroom.space.web.dto.CreateChannelRequest;
import pt.vclubroom.space.web.dto.CreateSpaceRequest;
import pt.vclubroom.space.web.dto.CreatedResponse;
import pt.vclubroom.space.web.dto.UpdateSpaceRequest;

/** Spaces (space.md §7): reading them, and managing them with SPACE_MANAGE. */
@RestController
class SpaceController {

    private final SpaceQueryService queries;
    private final SpaceService spaces;

    SpaceController(SpaceQueryService queries, SpaceService spaces) {
        this.queries = queries;
        this.spaces = spaces;
    }

    @GetMapping("/api/clubs/{clubId}/spaces")
    List<SpaceView> spaces(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("clubId") UUID clubId,
                           @RequestParam(name = "includeArchived", defaultValue = "false") boolean includeArchived) {
        return queries.spacesOf(clubId, me.actor(), includeArchived);
    }

    @GetMapping("/api/spaces/{spaceId}")
    SpaceView space(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("spaceId") UUID spaceId) {
        return queries.space(spaceId, me.actor());
    }

    @PostMapping("/api/clubs/{clubId}/spaces")
    @ResponseStatus(HttpStatus.CREATED)
    CreatedResponse create(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("clubId") UUID clubId,
                           @Valid @RequestBody CreateSpaceRequest request) {
        Space space = spaces.create(me.actor(), clubId, request.name(), request.description(),
            request.readRule().toRule(), request.writeRule().toRule());
        return new CreatedResponse(space.id(), space.slug());
    }

    @PatchMapping("/api/spaces/{spaceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void update(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("spaceId") UUID spaceId,
                @Valid @RequestBody UpdateSpaceRequest request) {
        spaces.update(me.actor(), spaceId, request.name(), request.description(),
            request.readRule() == null ? null : request.readRule().toRule(),
            request.writeRule() == null ? null : request.writeRule().toRule());
        if (request.position() != null) {
            spaces.move(me.actor(), spaceId, request.position());
        }
    }

    @PostMapping("/api/spaces/{spaceId}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void archive(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("spaceId") UUID spaceId) {
        spaces.archive(me.actor(), spaceId);
    }

    @PostMapping("/api/spaces/{spaceId}/channels")
    @ResponseStatus(HttpStatus.CREATED)
    CreatedResponse addChannel(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("spaceId") UUID spaceId,
                               @Valid @RequestBody CreateChannelRequest request) {
        Channel channel = spaces.addChannel(me.actor(), spaceId, request.type(), request.name());
        return new CreatedResponse(channel.id(), channel.slug());
    }
}
