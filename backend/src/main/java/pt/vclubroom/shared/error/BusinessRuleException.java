package pt.vclubroom.shared.error;

/** A request that is well-formed but violates a business rule (HTTP 422). */
public class BusinessRuleException extends DomainException {

    public BusinessRuleException(String code, String message) {
        super(code, message);
    }
}
