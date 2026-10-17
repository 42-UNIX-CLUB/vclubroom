package pt.vclubroom.shared.config;

import java.time.Duration;
import org.springframework.modulith.events.CompletedEventPublications;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Retries publications whose listener failed (e.g. transient DB/SMTP errors) and purges old completed ones.
 * With N instances, a publication can occasionally be retried twice; listeners are idempotent by design.
 */
@Component
class EventPublicationMaintenance {

    private final IncompleteEventPublications incomplete;
    private final CompletedEventPublications completed;

    EventPublicationMaintenance(IncompleteEventPublications incomplete, CompletedEventPublications completed) {
        this.incomplete = incomplete;
        this.completed = completed;
    }

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
    void resubmitStuckPublications() {
        incomplete.resubmitIncompletePublicationsOlderThan(Duration.ofMinutes(2));
    }

    @Scheduled(cron = "0 30 3 * * *")
    void purgeCompletedPublications() {
        completed.deletePublicationsOlderThan(Duration.ofDays(7));
    }
}
