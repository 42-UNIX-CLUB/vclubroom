package pt.vclubroom.group.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pt.vclubroom.group.api.GroupScope;
import pt.vclubroom.group.domain.Group;

public interface GroupRepository extends JpaRepository<Group, UUID> {

    List<Group> findByIdIn(Collection<UUID> ids);

    List<Group> findByScopeAndClubIdOrderByNameAsc(GroupScope scope, UUID clubId);

    boolean existsByScopeAndClubIdAndSlug(GroupScope scope, UUID clubId, String slug);

    /** Synced campus groups only: a manual group that happens to have such a slug is never filled by the sync. */
    @Query("""
           select g.id from Group g
           where g.scope = pt.vclubroom.group.api.GroupScope.CAMPUS and g.source = pt.vclubroom.group.domain.GroupSource.SYNCED
             and g.slug in :slugs
           """)
    Set<UUID> findSyncedCampusIdsBySlugs(@Param("slugs") Collection<String> slugs);

    /** The campus sync (E13-S04): two logins at once both try, one wins, neither fails (ux_groups_campus_slug). */
    @Modifying
    @Query(value = """
           insert into groups.groups (id, scope, club_id, name, slug, source, created_at, version)
           values (gen_random_uuid(), 'CAMPUS', null, :name, :slug, 'SYNCED', now(), 0)
           on conflict (slug) where scope = 'CAMPUS' do nothing
           """, nativeQuery = true)
    void insertSyncedIfAbsent(@Param("slug") String slug, @Param("name") String name);
}
