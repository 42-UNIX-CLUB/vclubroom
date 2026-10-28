package pt.vclubroom.space.application;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import pt.vclubroom.group.events.GroupDeleted;
import pt.vclubroom.space.persistence.SpaceRepository;

/**
 * A deleted group leaves every rule that named it. A GROUPS rule left without groups lets nobody in (fail closed),
 * until a manager picks other groups (E05-S03). Idempotent: a second delivery finds no space naming the group.
 */
@Component
class GroupDeletedListener {

    private final SpaceRepository spaces;

    GroupDeletedListener(SpaceRepository spaces) {
        this.spaces = spaces;
    }

    @ApplicationModuleListener
    void on(GroupDeleted event) {
        spaces.findReferencingGroup(event.groupId()).forEach(space -> space.removeGroupFromRules(event.groupId()));
    }
}
