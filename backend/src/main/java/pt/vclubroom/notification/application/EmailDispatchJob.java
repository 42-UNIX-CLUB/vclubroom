package pt.vclubroom.notification.application;

import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.identity.api.UserContact;
import pt.vclubroom.identity.api.UserDirectory;
import pt.vclubroom.notification.domain.Notification;
import pt.vclubroom.notification.persistence.NotificationRepository;

/**
 * Email outbox worker. Safe with N instances (FOR UPDATE SKIP LOCKED); at most 5 attempts with exponential backoff
 * (R-27), so a provider limit (e.g. Resend's 100/day) or an outage doesn't burn all retries at once. A failure is
 * recorded on the row and never reaches the event listeners that created it.
 */
@Component
class EmailDispatchJob {

    private static final Logger log = LoggerFactory.getLogger(EmailDispatchJob.class);
    private static final int BATCH = 50;
    static final int MAX_ATTEMPTS = 5;

    private final NotificationRepository notifications;
    private final UserDirectory users;
    private final EmailSender emailSender;
    private final Clock clock;
    private final String publicUrl;

    EmailDispatchJob(NotificationRepository notifications, UserDirectory users, EmailSender emailSender, Clock clock,
                     @Value("${app.public-url}") String publicUrl) {
        this.notifications = notifications;
        this.users = users;
        this.emailSender = emailSender;
        this.clock = clock;
        this.publicUrl = publicUrl.replaceAll("/+$", "");
    }

    @Scheduled(fixedDelayString = "${app.mail.poll-interval:PT30S}", initialDelayString = "${app.mail.poll-interval:PT30S}")
    @Transactional
    public void sendPendingEmails() {
        Instant now = clock.instant();
        for (Notification n : notifications.claimPendingEmails(MAX_ATTEMPTS, now, BATCH)) {
            UserContact contact = users.contactOf(n.recipientId()).orElse(null);
            if (contact == null || !contact.active() || contact.email() == null || contact.email().isBlank()) {
                n.emailSkipped();
                continue;
            }
            try {
                emailSender.send(new OutgoingEmail(contact.email(), "[Virtual Clubroom] " + n.title(), text(n)));
                n.emailSent(now);
            } catch (EmailDeliveryException e) {
                log.warn("Email for notification {} failed (attempt {}): {}", n.id(), n.emailAttempts() + 1, e.getMessage());
                n.emailFailed(e.getMessage(), now);
            }
        }
    }

    private String text(Notification n) {
        StringBuilder text = new StringBuilder();
        if (n.body() != null && !n.body().isBlank()) text.append(n.body()).append("\n\n");
        if (n.link() != null) text.append("Open: ").append(publicUrl).append(n.link()).append("\n\n");
        return text.append("--\nChoose which emails you get: ").append(publicUrl).append("/settings\n").toString();
    }
}
