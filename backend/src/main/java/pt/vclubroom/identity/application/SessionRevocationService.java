package pt.vclubroom.identity.application;

import java.util.UUID;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

/**
 * Deletes a user's Redis sessions (principal name = user UUID, indexed: spring.session.redis.repository-type=indexed).
 * Their next request gets a 401 (BR-24).
 */
@Service
public class SessionRevocationService {

    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    public SessionRevocationService(FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.sessions = sessions;
    }

    /** On disable, role change or a password reset by an admin. */
    public void revokeAll(UUID userId) {
        revokeAllExcept(userId, null);
    }

    /** After changing your own password: every other browser is logged out, this one stays. */
    public void revokeAllExcept(UUID userId, String keptSessionId) {
        sessions.findByPrincipalName(userId.toString()).keySet().stream()
            .filter(id -> !id.equals(keptSessionId))
            .forEach(sessions::deleteById);
    }
}
