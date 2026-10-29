package pt.vclubroom.group.application;

import java.time.Clock;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import pt.vclubroom.group.domain.GroupMember;
import pt.vclubroom.group.persistence.GroupMemberRepository;
import pt.vclubroom.group.persistence.GroupRepository;
import pt.vclubroom.identity.events.UserProfileSynced;

/**
 * Campus group sync at every 42 login (group.md §8.1): the synced groups the profile says, created on first need,
 * then the person is added to the missing ones and removed from the others. Running it twice changes nothing;
 * manual groups (E15-S03) are never touched.
 */
@Component
class CampusGroupSyncListener {

    private final GroupRepository groups;
    private final GroupMemberRepository members;
    private final CampusSyncRules rules;
    private final Clock clock;
    private final boolean enabled;

    CampusGroupSyncListener(GroupRepository groups, GroupMemberRepository members, CampusSyncRules rules, Clock clock,
                            @Value("${app.fortytwo.campus-groups-enabled:true}") boolean enabled) {
        this.groups = groups;
        this.members = members;
        this.rules = rules;
        this.clock = clock;
        this.enabled = enabled;
    }

    @ApplicationModuleListener
    void on(UserProfileSynced event) {
        if (!enabled) {
            return;
        }
        Map<String, String> desired = rules.desiredGroups(event);
        desired.forEach((slug, name) -> groups.insertSyncedIfAbsent(slug, name));

        Set<UUID> desiredIds = desired.isEmpty() ? Set.of() : groups.findSyncedCampusIdsBySlugs(desired.keySet());
        Set<UUID> currentIds = members.findSyncedGroupIdsOfUser(event.userId());

        Set<UUID> toRemove = new HashSet<>(currentIds);
        toRemove.removeAll(desiredIds);
        if (!toRemove.isEmpty()) {
            members.deleteMemberships(event.userId(), toRemove);
        }
        desiredIds.stream()
            .filter(id -> !currentIds.contains(id))
            .forEach(id -> members.save(GroupMember.of(id, event.userId(), null, clock.instant())));
    }
}
