package pt.vclubroom.shared.error;

/** Base type of every expected business error. The code is stable and documented in 05-api-reference.md. */
public abstract class DomainException extends RuntimeException {

    private final String code;

    protected DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
