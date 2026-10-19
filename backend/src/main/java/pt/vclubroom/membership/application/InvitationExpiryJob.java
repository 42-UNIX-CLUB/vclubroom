package pt.vclubroom.membership.application;

import java.time.Clock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.membership.persistence.OwnershipInvitationRepository;

/** R-11: invitations expire after 7 days. Accepting already refuses expired ones; this makes them leave the lists. */
@Component
class InvitationExpiryJob {

    private final OwnershipInvitationRepository invitations;
    private final Clock clock;

    InvitationExpiryJob(OwnershipInvitationRepository invitations, Clock clock) {
        this.invitations = invitations;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
    @Transactional
    void expire() {
        invitations.expireAllBefore(clock.instant());
    }
}
