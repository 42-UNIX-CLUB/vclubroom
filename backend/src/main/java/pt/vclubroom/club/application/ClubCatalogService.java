package pt.vclubroom.club.application;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pt.vclubroom.club.api.ClubCatalog;
import pt.vclubroom.club.api.ClubSnapshot;
import pt.vclubroom.club.domain.Club;
import pt.vclubroom.club.persistence.ClubRepository;
import pt.vclubroom.shared.error.NotFoundException;

/** Implementation of the public API. Returns snapshots, never entities. */
@Service
@Transactional(readOnly = true)
class ClubCatalogService implements ClubCatalog {

    private final ClubRepository clubs;

    ClubCatalogService(ClubRepository clubs) {
        this.clubs = clubs;
    }

    @Override
    public Optional<ClubSnapshot> findById(UUID clubId) {
        return clubs.findById(clubId).map(ClubCatalogService::snapshot);
    }

    @Override
    public Optional<ClubSnapshot> findBySlug(String slug) {
        return clubs.findBySlug(slug).map(ClubCatalogService::snapshot);
    }

    @Override
    public Map<UUID, ClubSnapshot> findByIds(Collection<UUID> clubIds) {
        if (clubIds.isEmpty()) {
            return Map.of();
        }
        return clubs.findByIdIn(clubIds).stream()
            .map(ClubCatalogService::snapshot)
            .collect(Collectors.toMap(ClubSnapshot::id, Function.identity()));
    }

    @Override
    public ClubSnapshot require(UUID clubId) {
        return findById(clubId).orElseThrow(() -> new NotFoundException("CLUB_NOT_FOUND", "Club not found"));
    }

    private static ClubSnapshot snapshot(Club c) {
        return new ClubSnapshot(c.id(), c.slug(), c.name(), c.status(), c.joinPolicy(), c.currentEra());
    }
}