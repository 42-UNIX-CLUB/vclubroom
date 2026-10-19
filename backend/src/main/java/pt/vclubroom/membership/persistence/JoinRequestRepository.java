package pt.vclubroom.membership.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pt.vclubroom.membership.domain.JoinRequest;
import pt.vclubroom.membership.domain.JoinRequestStatus;

public interface JoinRequestRepository extends JpaRepository<JoinRequest, UUID> {

    @Query("""
           select count(r) > 0 from JoinRequest r
           where r.clubId = :clubId and r.userId = :userId
             and r.status = pt.vclubroom.membership.domain.JoinRequestStatus.PENDING
           """)
    boolean existsPending(@Param("clubId") UUID clubId, @Param("userId") UUID userId);

    @Query("""
           select r from JoinRequest r
           where r.clubId = :clubId and r.userId = :userId
             and r.status = pt.vclubroom.membership.domain.JoinRequestStatus.PENDING
           """)
    Optional<JoinRequest> findPending(@Param("clubId") UUID clubId, @Param("userId") UUID userId);

    /** A club's queue, oldest first (first come, first served). */
    List<JoinRequest> findByClubIdAndStatusOrderByCreatedAtAsc(UUID clubId, JoinRequestStatus status);

    default List<JoinRequest> findAllPending(UUID clubId) {
        return findByClubIdAndStatusOrderByCreatedAtAsc(clubId, JoinRequestStatus.PENDING);
    }

    /** R-10: archiving a club closes its open requests, in one statement (decidedBy null: a system decision). */
    @Modifying
    @Query("""
           update JoinRequest r
           set r.status = pt.vclubroom.membership.domain.JoinRequestStatus.CANCELLED, r.decidedAt = :now,
               r.version = r.version + 1
           where r.clubId = :clubId and r.status = pt.vclubroom.membership.domain.JoinRequestStatus.PENDING
           """)
    int cancelAllPending(@Param("clubId") UUID clubId, @Param("now") Instant now);
}
