package pt.vclubroom.realtime.infrastructure;

import java.security.Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * One log line per live connection opened or closed, naming the instance (its host name: the container ID under
 * Docker). With several backends (E16-S04), `docker compose logs backend` shows browsers spread over the instances
 * while their messages still reach each other through Redis. The user is logged by ID only.
 */
@Component
class LiveConnectionLog {

    private static final Logger log = LoggerFactory.getLogger(LiveConnectionLog.class);
    private static final String INSTANCE = System.getenv().getOrDefault("HOSTNAME", "local");

    @EventListener
    void opened(SessionConnectedEvent event) {
        log.info("Live connection opened on {} for user {}", INSTANCE, name(event.getUser()));
    }

    @EventListener
    void closed(SessionDisconnectEvent event) {
        log.info("Live connection closed on {} for user {}", INSTANCE, name(event.getUser()));
    }

    private static String name(Principal user) {
        return user == null ? "anonymous" : user.getName();
    }
}
