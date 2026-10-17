package pt.vclubroom.shared.error;

/** A malformed request that Bean Validation cannot express, e.g. an undecodable cursor (HTTP 400). */
public class InvalidRequestException extends DomainException {

    public InvalidRequestException(String code, String message) {
        super(code, message);
    }
}
