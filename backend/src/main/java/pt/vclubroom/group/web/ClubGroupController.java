package pt.vclubroom.group.web;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pt.vclubroom.group.application.ClubGroupService;
import pt.vclubroom.group.application.ClubGroupView;
import pt.vclubroom.group.application.GroupMemberView;
import pt.vclubroom.group.web.dto.GroupRequest;
import pt.vclubroom.shared.error.InvalidRequestException;
import pt.vclubroom.shared.security.AuthenticatedUser;

/** A club's groups and their members (group.md §7). */
@RestController
@RequestMapping("/api/clubs/{clubId}/groups")
class ClubGroupController {

    private final ClubGroupService groups;

    ClubGroupController(ClubGroupService groups) {
        this.groups = groups;
    }

    @GetMapping
    List<ClubGroupView> list(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("clubId") UUID clubId) {
        return groups.list(me.actor(), clubId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ClubGroupView create(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("clubId") UUID clubId,
                         @Valid @RequestBody GroupRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new InvalidRequestException("GROUP_NAME_REQUIRED", "A group needs a name.");
        }
        return groups.create(me.actor(), clubId, request.name(), request.description());
    }

    @PatchMapping("/{groupId}")
    ClubGroupView update(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("clubId") UUID clubId,
                         @PathVariable("groupId") UUID groupId, @Valid @RequestBody GroupRequest request) {
        return groups.update(me.actor(), clubId, groupId, request.name(), request.description());
    }

    @DeleteMapping("/{groupId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("clubId") UUID clubId,
                @PathVariable("groupId") UUID groupId) {
        groups.delete(me.actor(), clubId, groupId);
    }

    @GetMapping("/{groupId}/members")
    List<GroupMemberView> members(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("clubId") UUID clubId,
                                  @PathVariable("groupId") UUID groupId) {
        return groups.members(me.actor(), clubId, groupId);
    }

    @PutMapping("/{groupId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void addMember(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("clubId") UUID clubId,
                   @PathVariable("groupId") UUID groupId, @PathVariable("userId") UUID userId) {
        groups.addMember(me.actor(), clubId, groupId, userId);
    }

    @DeleteMapping("/{groupId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeMember(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("clubId") UUID clubId,
                      @PathVariable("groupId") UUID groupId, @PathVariable("userId") UUID userId) {
        groups.removeMember(me.actor(), clubId, groupId, userId);
    }
}
