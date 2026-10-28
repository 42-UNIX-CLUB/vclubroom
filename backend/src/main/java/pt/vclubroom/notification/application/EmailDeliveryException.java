package pt.vclubroom.notification.application;

public class EmailDeliveryException extends RuntimeException {

    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
