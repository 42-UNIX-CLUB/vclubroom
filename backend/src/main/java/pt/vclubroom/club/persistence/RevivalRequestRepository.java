package pt.vclubroom.club.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import pt.vclubroom.club.domain.RevivalRequest;
import pt.vclubroom.club.domain.RevivalStatus;

public interface RevivalRequestRepository extends JpaRepository<RevivalRequest, UUID> {

    boolean existsByClubIdAndRequesterIdAndStatus(UUID clubId, UUID requesterId, RevivalStatus status);

    List<RevivalRequest> findByClubIdAndStatus(UUID clubId, RevivalStatus status);

    Page<RevivalRequest> findByStatus(RevivalStatus status, Pageable pageable);
}
