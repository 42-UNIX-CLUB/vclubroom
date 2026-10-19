package pt.vclubroom.membership.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pt.vclubroom.membership.domain.OwnershipInvitation;

/** E13-S01 adds what sending, answering and expiring invitations need. */
public interface OwnershipInvitationRepository extends JpaRepository<OwnershipInvitation, UUID> {

    @Query("""
           select i from OwnershipInvitation i
           where i.clubId = :clubId and i.inviteeId = :inviteeId
             and i.status = pt.vclubroom.membership.domain.InvitationStatus.PENDING
           """)
    Optional<OwnershipInvitation> findPending(@Param("clubId") UUID clubId, @Param("inviteeId") UUID inviteeId);
}
