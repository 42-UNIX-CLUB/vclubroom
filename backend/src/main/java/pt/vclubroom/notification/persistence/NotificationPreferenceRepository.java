package pt.vclubroom.notification.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pt.vclubroom.notification.domain.NotificationPreference;
import pt.vclubroom.notification.domain.NotificationType;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, NotificationPreference.Key> {

    List<NotificationPreference> findByUserIdInAndType(Collection<UUID> userIds, NotificationType type);

    List<NotificationPreference> findByUserId(UUID userId);
}
