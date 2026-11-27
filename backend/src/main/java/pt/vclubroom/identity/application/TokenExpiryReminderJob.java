package pt.vclubroom.identity.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** A week before a personal access token expires, its owner is told (E20-S02), in the app and by email. */
@Component
class TokenExpiryReminderJob {

    private final AccessTokenService tokens;

    TokenExpiryReminderJob(AccessTokenService tokens) {
        this.tokens = tokens;
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
    void remind() {
        tokens.remindExpiring();
    }
}
