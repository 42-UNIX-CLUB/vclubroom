package pt.vclubroom.space.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pt.vclubroom.space.domain.Space;

public interface SpaceRepository extends JpaRepository<Space, UUID> {

    List<Space> findByClubIdOrderByPositionAsc(UUID clubId);

    boolean existsByClubId(UUID clubId);

    List<Space> findByIdIn(Collection<UUID> ids);

    /** Spaces whose read or write rule names this group (GroupDeletedListener). */
    @Query(value = """
           select * from space.spaces
           where :groupId = any(read_group_ids) or :groupId = any(write_group_ids)
           """, nativeQuery = true)
    List<Space> findReferencingGroup(@Param("groupId") UUID groupId);
}
