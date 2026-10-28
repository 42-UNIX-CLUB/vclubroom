package pt.vclubroom.notification.application;

/** Provider-neutral email. Plain text keeps every provider and spam filter happy (HTML is an extension). */
public record OutgoingEmail(String to, String subject, String textBody) {
}
