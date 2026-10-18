package pt.vclubroom.club.api;

/** OPEN: anyone joins at once. APPROVAL: a request an owner accepts (the default, D-10). */
public enum JoinPolicy {
    OPEN,
    APPROVAL
}