package pt.vclubroom.notification.application;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.notification.domain.NotificationType;
import pt.vclubroom.notification.persistence.NotificationPreferenceRepository;

/** Stored overrides win; otherwise the type's defaults apply. E12-S02 adds reading and editing them. */
@Service
public class PreferenceService {

    public record Effective(boolean inApp, boolean email) { }

    private final NotificationPreferenceRepository preferences;

    PreferenceService(NotificationPreferenceRepository preferences) {
        this.preferences = preferences;
    }

    @Transactional(readOnly = true)
    public Map<UUID, Effective> effective(Collection<UUID> userIds, NotificationType type) {
        Map<UUID, Effective> result = new HashMap<>();
        userIds.forEach(id -> result.put(id, new Effective(type.defaultInApp(), type.defaultEmail())));
        preferences.findByUserIdInAndType(userIds, type).forEach(p -> result.put(p.userId(), new Effective(p.inApp(), p.email())));
        return result;
    }
}
