package pt.vclubroom.support.domain;

/** What a ticket is about. Each type is a type/… label in the tracker (deploy/forgejo/labels.txt). */
public enum TicketType {
    BUG,
    SUGGESTION,
    QUESTION,
    ACCOUNT,         // club or account problem: joining, ownership, a club to restore, logging in
    REPORT,          // content or behaviour that breaks the rules
    ACCESSIBILITY,
    PRIVACY,         // access, correction or deletion of personal data
    OTHER
}
