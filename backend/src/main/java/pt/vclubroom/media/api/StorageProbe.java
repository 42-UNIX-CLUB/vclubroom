package pt.vclubroom.media.api;

/**
 * Whether file storage answers, for the public status (E19-S02). Not a health indicator: like SMTP, a storage outage
 * must not mark the whole API as DOWN.
 */
public interface StorageProbe {

    /** True when the storage answers within 2 s. */
    boolean reachable();
}
