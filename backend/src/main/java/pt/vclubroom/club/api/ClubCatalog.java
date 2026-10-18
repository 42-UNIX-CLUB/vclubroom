package pt.vclubroom.club.api;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** How other modules (membership, space, calendar…) look up clubs. Implemented by club.application.ClubCatalogService. */
public interface ClubCatalog {

    Optional<ClubSnapshot> findById(UUID clubId);

    Optional<ClubSnapshot> findBySlug(String slug);

    /** Batch lookup; missing IDs are simply absent. */
    Map<UUID, ClubSnapshot> findByIds(Collection<UUID> clubIds);

    /** @throws pt.vclubroom.shared.error.NotFoundException when the club does not exist */
    ClubSnapshot require(UUID clubId);
}