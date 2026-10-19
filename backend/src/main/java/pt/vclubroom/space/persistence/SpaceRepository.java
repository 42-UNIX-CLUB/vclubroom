package pt.vclubroom.space.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pt.vclubroom.space.domain.Space;

/** E13-S03 adds findReferencingGroup (spaces whose rules name a deleted group). */
public interface SpaceRepository extends JpaRepository<Space, UUID> {

    List<Space> findByClubIdOrderByPositionAsc(UUID clubId);

    boolean existsByClubId(UUID clubId);

    List<Space> findByIdIn(Collection<UUID> ids);
}
