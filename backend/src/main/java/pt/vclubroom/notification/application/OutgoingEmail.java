package pt.vclubroom.notification.application;

/**
 * Provider-neutral email. Plain text keeps every provider and spam filter happy (HTML is an extension). from: null for
 * the configured sender (MAIL_FROM); support emails set their own (E17-S03).
 */
public record OutgoingEmail(String to, String subject, String textBody, String from) {

    public OutgoingEmail(String to, String subject, String textBody) {
        this(to, subject, textBody, null);
    }
}
