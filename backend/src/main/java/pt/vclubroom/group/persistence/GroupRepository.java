package pt.vclubroom.group.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pt.vclubroom.group.api.GroupScope;
import pt.vclubroom.group.domain.Group;

/** E13-S04 adds the campus sync queries (insertSyncedIfAbsent, findCampusIdsBySlugs). */
public interface GroupRepository extends JpaRepository<Group, UUID> {

    List<Group> findByIdIn(Collection<UUID> ids);

    List<Group> findByScopeAndClubIdOrderByNameAsc(GroupScope scope, UUID clubId);

    boolean existsByScopeAndClubIdAndSlug(GroupScope scope, UUID clubId, String slug);
}
