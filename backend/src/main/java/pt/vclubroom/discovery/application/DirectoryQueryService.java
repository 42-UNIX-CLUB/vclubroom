package pt.vclubroom.discovery.application;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.discovery.domain.ClubCard;
import pt.vclubroom.discovery.persistence.ClubCardRepository;
import pt.vclubroom.discovery.persistence.DirectorySearchRepository;
import pt.vclubroom.discovery.persistence.DirectorySearchRepository.ClubCardRow;
import pt.vclubroom.discovery.persistence.DirectorySearchRepository.Criteria;
import pt.vclubroom.discovery.persistence.DirectorySearchRepository.Totals;
import pt.vclubroom.shared.web.PageResponse;

/** Read side of the public landing page, directory and club profiles: only the read model, never other modules. */
@Service
@Transactional(readOnly = true)
public class DirectoryQueryService {

    private static final int LANDING_LIST_SIZE = 6;
    private static final int MAX_PAGE_SIZE = 50;

    private final DiscoveryProperties properties;
    private final DirectorySearchRepository search;
    private final ClubCardRepository cards;

    public DirectoryQueryService(DiscoveryProperties properties, DirectorySearchRepository search, ClubCardRepository cards) {
        this.properties = properties;
        this.search = search;
        this.cards = cards;
    }

    public List<String> categories() {
        return properties.categories();
    }

    public PageResponse<ClubCardRow> search(DirectoryQuery query) {
        int size = Math.clamp(query.size(), 1, MAX_PAGE_SIZE);
        return search.search(new Criteria(blankToNull(query.q()), blankToNull(query.category()), blankToNull(query.tag()),
            query.status().statuses(), query.sort(), Math.max(query.page(), 0), size));
    }

    public Landing landing() {
        Totals totals = search.totals();
        Map<String, Long> perCategory = search.activeClubsPerCategory();
        return new Landing(
            new Landing.Stats(totals.activeClubs(), totals.archivedClubs(), totals.memberships()),
            firstActive("active"),
            firstActive("new"),
            properties.categories().stream()                              // configured order, zero included
                .map(name -> new Landing.CategoryCount(name, perCategory.getOrDefault(name, 0L)))
                .toList());
    }

    /** Pending and rejected clubs have no card, so they are simply not found (BR-03). */
    public Optional<ClubCard> profile(String slug) {
        return cards.findBySlug(slug);
    }

    private List<ClubCardRow> firstActive(String sort) {
        return search.search(new Criteria(null, null, null, List.of("ACTIVE"), sort, 0, LANDING_LIST_SIZE)).items();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
