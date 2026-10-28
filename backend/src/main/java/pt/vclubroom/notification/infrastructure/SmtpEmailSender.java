package pt.vclubroom.notification.infrastructure;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import pt.vclubroom.notification.application.EmailDeliveryException;
import pt.vclubroom.notification.application.EmailSender;
import pt.vclubroom.notification.application.OutgoingEmail;

/** Adapter for any SMTP provider (Mailpit, Resend, Brevo, SES, bocal…), selected purely by spring.mail.* config. */
@Component
@ConditionalOnProperty(name = "app.mail.transport", havingValue = "smtp", matchIfMissing = true)
class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final AppMailProperties properties;

    SmtpEmailSender(JavaMailSender mailSender, AppMailProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void send(OutgoingEmail email) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.from());            // must be on the provider's verified domain (R-25)
            if (properties.replyTo() != null && !properties.replyTo().isBlank()) {
                helper.setReplyTo(properties.replyTo());
            }
            helper.setTo(email.to());
            helper.setSubject(email.subject());
            helper.setText(email.textBody(), false);
            mailSender.send(message);
        } catch (MessagingException | MailException e) {
            throw new EmailDeliveryException("SMTP delivery failed: " + e.getMessage(), e);
        }
    }
}
