package pt.vclubroom.notification.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import pt.vclubroom.notification.application.EmailSender;
import pt.vclubroom.notification.application.OutgoingEmail;

/** MAIL_TRANSPORT=log: no SMTP at all (offline development, CI). */
@Component
@ConditionalOnProperty(name = "app.mail.transport", havingValue = "log")
class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(OutgoingEmail email) {
        log.info("[mail] to={} from={} subject={}\n{}", email.to(), email.from() == null ? "default" : email.from(),
            email.subject(), email.textBody());
    }
}
