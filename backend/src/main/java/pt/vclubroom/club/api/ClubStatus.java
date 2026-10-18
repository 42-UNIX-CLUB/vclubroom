package pt.vclubroom.club.api;

/** Lifecycle of a club: requested → approved (ACTIVE) or REJECTED (final); ACTIVE ⇄ ARCHIVED. */
public enum ClubStatus {
    PENDING_APPROVAL,
    ACTIVE,
    REJECTED,
    ARCHIVED
}