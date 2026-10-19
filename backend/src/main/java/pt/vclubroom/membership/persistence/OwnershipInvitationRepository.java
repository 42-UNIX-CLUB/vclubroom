package pt.vclubroom.membership.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pt.vclubroom.membership.domain.OwnershipInvitation;

/** E14-S01 adds expiring all pending invitations of an archived club. */
public interface OwnershipInvitationRepository extends JpaRepository<OwnershipInvitation, UUID> {

    @Query("""
           select i from OwnershipInvitation i
           where i.clubId = :clubId and i.inviteeId = :inviteeId
             and i.status = pt.vclubroom.membership.domain.InvitationStatus.PENDING
           """)
    Optional<OwnershipInvitation> findPending(@Param("clubId") UUID clubId, @Param("inviteeId") UUID inviteeId);

    /** The invitee's inbox, newest first. */
    @Query("""
           select i from OwnershipInvitation i
           where i.inviteeId = :inviteeId and i.status = pt.vclubroom.membership.domain.InvitationStatus.PENDING
           order by i.createdAt desc
           """)
    List<OwnershipInvitation> findPendingForInvitee(@Param("inviteeId") UUID inviteeId);

    /** What the owners of a club have sent and is still waiting. */
    @Query("""
           select i from OwnershipInvitation i
           where i.clubId = :clubId and i.status = pt.vclubroom.membership.domain.InvitationStatus.PENDING
           order by i.createdAt desc
           """)
    List<OwnershipInvitation> findPendingForClub(@Param("clubId") UUID clubId);

    /** R-11: one statement for every expired invitation; harmless when several instances run it at once. */
    @Modifying
    @Query("""
           update OwnershipInvitation i
           set i.status = pt.vclubroom.membership.domain.InvitationStatus.EXPIRED, i.respondedAt = :now,
               i.version = i.version + 1
           where i.status = pt.vclubroom.membership.domain.InvitationStatus.PENDING and i.expiresAt < :now
           """)
    int expireAllBefore(@Param("now") Instant now);
}
