package pt.vclubroom.shared.error;

public class ConflictException extends DomainException {

    public ConflictException(String code, String message) {
        super(code, message);
    }
}
