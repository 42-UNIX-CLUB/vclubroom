package pt.vclubroom.notification.application;

/** Port: the notification module never knows which provider delivers its emails (D-35). */
public interface EmailSender {

    /** @throws EmailDeliveryException when the provider rejects the email or is unreachable (the job retries). */
    void send(OutgoingEmail email);
}
