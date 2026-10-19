package pt.vclubroom.realtime.api;

import java.util.UUID;

/**
 * Best-effort push to browsers. Called inside a transaction, the push waits for the commit (and is dropped on
 * rollback). Payloads are broadcast to every subscriber, so they must not depend on who is looking.
 */
public interface RealtimePublisher {

    void toTopic(String destination, String type, Object payload);

    /** destination is relative to the user prefix, e.g. "/queue/notifications". */
    void toUser(UUID userId, String destination, String type, Object payload);
}
