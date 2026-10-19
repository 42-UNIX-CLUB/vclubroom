package pt.vclubroom.identity.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** How other modules look up users. Implemented by identity.application.UserDirectoryService. */
public interface UserDirectory {

    Optional<UserSummary> findById(UUID userId);

    /** By login, ignoring case (the demo seeders know their people by login). */
    Optional<UserSummary> findByLogin(String login);

    /** Batch lookup used to decorate lists (authors, members). Missing IDs are simply absent. */
    Map<UUID, UserSummary> findByIds(Collection<UUID> userIds);

    Optional<UserContact> contactOf(UUID userId);

    List<UUID> adminIds();
}
