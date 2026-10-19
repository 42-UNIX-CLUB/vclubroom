package pt.vclubroom.discovery.persistence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pt.vclubroom.shared.web.PageResponse;

/** Dynamic directory search: only the filters present end up in the SQL (discovery.md §7.2). */
@Repository
public class DirectorySearchRepository {

    public record ClubCardRow(UUID clubId, String slug, String name, String tagline, String category,
                              String avatarUrl, String status, String joinPolicy, int memberCount,
                              Instant lastActivityAt, Instant foundedAt) { }

    public record Criteria(String q, String category, String tag, List<String> statuses, String sort,
                           int page, int size) { }

    public record Totals(long activeClubs, long archivedClubs, long memberships) { }

    private final JdbcClient jdbc;

    public DirectorySearchRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public PageResponse<ClubCardRow> search(Criteria c) {
        StringBuilder where = new StringBuilder(" where status in (:statuses)");
        List<String> extra = new ArrayList<>();
        if (c.q() != null && !c.q().isBlank()) {
            where.append(" and (search @@ plainto_tsquery('simple', :q) or name ilike '%' || :q || '%')");
            extra.add("q");
        }
        if (c.category() != null) {
            where.append(" and category = :category");
            extra.add("category");
        }
        if (c.tag() != null) {
            where.append(" and tags @> array[cast(:tag as text)]");   // GIN-indexable
            extra.add("tag");
        }
        String orderBy = switch (c.sort() == null ? "active" : c.sort()) {
            case "new" -> " order by founded_at desc nulls last";
            case "members" -> " order by member_count desc, name";
            case "name" -> " order by name";
            default -> " order by last_activity_at desc nulls last, member_count desc";
        };

        var itemsQuery = jdbc.sql("""
                select club_id, slug, name, tagline, category, avatar_url, status, join_policy, member_count,
                       last_activity_at, founded_at
                from discovery.club_cards""" + where + orderBy + " limit :limit offset :offset")
            .param("statuses", c.statuses())
            .param("limit", c.size())
            .param("offset", c.page() * c.size());
        var countQuery = jdbc.sql("select count(*) from discovery.club_cards" + where)
            .param("statuses", c.statuses());
        if (extra.contains("q")) { itemsQuery = itemsQuery.param("q", c.q()); countQuery = countQuery.param("q", c.q()); }
        if (extra.contains("category")) { itemsQuery = itemsQuery.param("category", c.category()); countQuery = countQuery.param("category", c.category()); }
        if (extra.contains("tag")) { itemsQuery = itemsQuery.param("tag", c.tag()); countQuery = countQuery.param("tag", c.tag()); }

        List<ClubCardRow> items = itemsQuery.query(ClubCardRow.class).list();
        long total = countQuery.query(Long.class).single();
        return new PageResponse<>(items, c.page(), c.size(), total);
    }

    /** The landing page's numbers in one pass over the table. */
    public Totals totals() {
        return jdbc.sql("""
                select count(*) filter (where status = 'ACTIVE')                     as active_clubs,
                       count(*) filter (where status = 'ARCHIVED')                   as archived_clubs,
                       coalesce(sum(member_count) filter (where status = 'ACTIVE'), 0) as memberships
                from discovery.club_cards""")
            .query(Totals.class).single();
    }

    /** Active clubs per category; categories without clubs are absent. */
    public Map<String, Long> activeClubsPerCategory() {
        return jdbc.sql("select category, count(*) as clubs from discovery.club_cards where status = 'ACTIVE' and category is not null group by category")
            .query((rs, n) -> Map.entry(rs.getString("category"), rs.getLong("clubs")))
            .list().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
