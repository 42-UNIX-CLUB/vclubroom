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
import pt.vclubroom.group.application.CampusGroupService;
import pt.vclubroom.group.application.CampusGroupView;
import pt.vclubroom.group.application.GroupMemberView;
import pt.vclubroom.group.web.dto.GroupRequest;
import pt.vclubroom.shared.error.InvalidRequestException;
import pt.vclubroom.shared.security.AuthenticatedUser;

/** Manual campus groups and their members (group.md §7, E15-S03). SecurityConfig limits /api/admin/** to admins. */
@RestController
@RequestMapping("/api/admin/campus-groups")
class AdminCampusGroupController {

    private final CampusGroupService groups;

    AdminCampusGroupController(CampusGroupService groups) {
        this.groups = groups;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CampusGroupView create(@AuthenticationPrincipal AuthenticatedUser me, @Valid @RequestBody GroupRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new InvalidRequestException("GROUP_NAME_REQUIRED", "A group needs a name.");
        }
        return groups.create(me.userId(), request.name(), request.description());
    }

    @PatchMapping("/{groupId}")
    CampusGroupView update(@PathVariable("groupId") UUID groupId, @Valid @RequestBody GroupRequest request) {
        return groups.update(groupId, request.name(), request.description());
    }

    @DeleteMapping("/{groupId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable("groupId") UUID groupId) {
        groups.delete(groupId);
    }

    @GetMapping("/{groupId}/members")
    List<GroupMemberView> members(@PathVariable("groupId") UUID groupId) {
        return groups.members(groupId);
    }

    @PutMapping("/{groupId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void add(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("groupId") UUID groupId, @PathVariable("userId") UUID userId) {
        groups.addMember(me.userId(), groupId, userId);
    }

    @DeleteMapping("/{groupId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@PathVariable("groupId") UUID groupId, @PathVariable("userId") UUID userId) {
        groups.removeMember(groupId, userId);
    }
}
