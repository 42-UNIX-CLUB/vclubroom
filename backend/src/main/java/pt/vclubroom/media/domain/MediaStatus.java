package pt.vclubroom.media.domain;

/** PENDING (slot issued) → READY (upload verified) → ATTACHED (used by a post, avatar…), or DELETED. */
public enum MediaStatus {
    PENDING,
    READY,
    ATTACHED,
    DELETED
}
