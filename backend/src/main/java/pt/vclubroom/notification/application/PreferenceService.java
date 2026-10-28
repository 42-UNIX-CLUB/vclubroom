package pt.vclubroom.notification.application;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.notification.domain.NotificationPreference;
import pt.vclubroom.notification.domain.NotificationType;
import pt.vclubroom.notification.persistence.NotificationPreferenceRepository;

/** Stored overrides win; otherwise the type's defaults apply (R-15). Only overrides are stored. */
@Service
public class PreferenceService {

    public record Effective(boolean inApp, boolean email) { }

    /** A person's choice for one type, as the form sends it. */
    public record Choice(NotificationType type, boolean inApp, boolean email) { }

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

    /** Every type, in the enum's order, with what applies to this person and the defaults. */
    @Transactional(readOnly = true)
    public List<PreferenceView> allFor(UUID userId) {
        Map<NotificationType, NotificationPreference> mine = preferences.findByUserId(userId).stream()
            .collect(Collectors.toMap(NotificationPreference::type, Function.identity()));
        return Arrays.stream(NotificationType.values()).map(type -> {
            NotificationPreference p = mine.get(type);
            return new PreferenceView(type, p == null ? type.defaultInApp() : p.inApp(), p == null ? type.defaultEmail() : p.email(),
                type.defaultInApp(), type.defaultEmail());
        }).toList();
    }

    /** A choice equal to the defaults removes the override; types not sent stay as they are. */
    @Transactional
    public List<PreferenceView> save(UUID userId, List<Choice> choices) {
        Map<NotificationType, NotificationPreference> mine = preferences.findByUserId(userId).stream()
            .collect(Collectors.toMap(NotificationPreference::type, Function.identity()));
        for (Choice choice : choices) {
            NotificationPreference existing = mine.get(choice.type());
            boolean isDefault = choice.inApp() == choice.type().defaultInApp() && choice.email() == choice.type().defaultEmail();
            if (isDefault) {
                if (existing != null) preferences.delete(existing);
            } else if (existing != null) {
                existing.change(choice.inApp(), choice.email());
            } else {
                preferences.save(NotificationPreference.of(userId, choice.type(), choice.inApp(), choice.email()));
            }
        }
        preferences.flush();
        return allFor(userId);
    }
}
