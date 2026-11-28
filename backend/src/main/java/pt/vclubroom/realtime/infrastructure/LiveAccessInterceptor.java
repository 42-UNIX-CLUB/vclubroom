package pt.vclubroom.realtime.infrastructure;

import java.security.Principal;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import pt.vclubroom.shared.security.AuthenticatedUser;

/**
 * Every push to a subscriber passes here (the client outbound channel). Access was checked at SUBSCRIBE, but it can be
 * lost afterwards: removed from the club, a space's rule changed, a role or group taken away. So each topic message is
 * checked again against the subscriber's current access, at most once per recheck interval per subscription, and
 * dropped for that subscriber when they can no longer read it. Each instance checks its own connections against the
 * shared database: nothing has to be told to every instance. /user/ queues are private by construction and pass.
 */
@Component
class LiveAccessInterceptor implements ChannelInterceptor {

    private record Verdict(boolean allowed, long checkedAtNanos) { }       // monotonic: wall-clock steps can't keep a stale yes

    private final TopicAccess access;
    private final long recheckNanos;
    private final Map<String, AuthenticatedUser> users = new ConcurrentHashMap<>();        // session id → who connected
    private final Map<String, Map<String, Verdict>> verdicts = new ConcurrentHashMap<>();  // session id → destination → last check

    LiveAccessInterceptor(TopicAccess access, RealtimeProperties properties) {
        this.access = access;
        Duration interval = properties.accessRecheckInterval() == null ? Duration.ofSeconds(10) : properties.accessRecheckInterval();
        this.recheckNanos = interval.toNanos();
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        var headers = message.getHeaders();
        String destination = SimpMessageHeaderAccessor.getDestination(headers);
        if (SimpMessageHeaderAccessor.getMessageType(headers) != SimpMessageType.MESSAGE
            || destination == null || !destination.startsWith("/topic/")) {
            return message;
        }
        String sessionId = SimpMessageHeaderAccessor.getSessionId(headers);
        AuthenticatedUser user = sessionId == null ? null : users.get(sessionId);
        if (user == null) {
            return null;                                                       // unknown connection: fail closed
        }
        Map<String, Verdict> mine = verdicts.computeIfAbsent(sessionId, id -> new ConcurrentHashMap<>());
        long now = System.nanoTime();
        Verdict verdict = mine.get(destination);
        if (verdict == null || now - verdict.checkedAtNanos() >= recheckNanos) {        // 0: every push is checked
            verdict = new Verdict(access.canRead(user.actor(), destination), now);
            mine.put(destination, verdict);
        }
        return verdict.allowed() ? message : null;
    }

    @EventListener
    void connected(SessionConnectEvent event) {
        String sessionId = SimpMessageHeaderAccessor.getSessionId(event.getMessage().getHeaders());
        Principal principal = event.getUser();
        if (sessionId != null && principal instanceof Authentication auth && auth.getPrincipal() instanceof AuthenticatedUser user) {
            users.put(sessionId, user);
        }
    }

    @EventListener
    void disconnected(SessionDisconnectEvent event) {
        users.remove(event.getSessionId());
        verdicts.remove(event.getSessionId());
    }
}
