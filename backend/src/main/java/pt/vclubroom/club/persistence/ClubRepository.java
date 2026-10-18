package pt.vclubroom.club.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import pt.vclubroom.club.api.ClubStatus;
import pt.vclubroom.club.domain.Club;

public interface ClubRepository extends JpaRepository<Club, UUID> {

    @EntityGraph(attributePaths = "eras")
    Optional<Club> findWithErasById(UUID id);

    @EntityGraph(attributePaths = "eras")
    Optional<Club> findWithErasBySlug(String slug);

    Optional<Club> findBySlug(String slug);

    boolean existsBySlug(String slug);

    long countByFounderIdAndStatus(UUID founderId, ClubStatus status);

    Page<Club> findByStatus(ClubStatus status, Pageable pageable);

    List<Club> findByFounderIdAndStatusInOrderByCreatedAtDesc(UUID founderId, Collection<ClubStatus> statuses);

    List<Club> findByIdIn(Collection<UUID> ids);
}