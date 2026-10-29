package pt.vclubroom.group.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import pt.vclubroom.identity.events.UserProfileSynced;

/**
 * Maps 42 profile facts to campus group slugs and names (group.md §9, D-21): campus, pool, cursus and alumni.
 * One place to add new rules.
 */
@Component
class CampusSyncRules {

    Map<String, String> desiredGroups(UserProfileSynced p) {
        Map<String, String> groups = new LinkedHashMap<>();
        orEmpty(p.campusNames()).forEach(c -> put(groups, "campus-", c, "Campus " + c));
        if (p.poolYear() != null && p.poolMonth() != null && !p.poolMonth().isBlank()) {
            String month = p.poolMonth().substring(0, 1).toUpperCase(Locale.ROOT) + p.poolMonth().substring(1);
            put(groups, "pool-" + p.poolYear() + "-", p.poolMonth(), "Pool " + month + " " + p.poolYear());
        }
        orEmpty(p.cursusSlugs()).forEach(c -> put(groups, "cursus-", c, "Cursus " + c));
        if (p.alumni()) {
            groups.put("alumni", "42 Alumni");
        }
        return groups;
    }

    /** A value with no letters or digits gives no group. */
    private static void put(Map<String, String> groups, String prefix, String value, String name) {
        String slug = value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (!slug.isEmpty()) {
            groups.put(prefix + slug, name);
        }
    }

    private static List<String> orEmpty(List<String> values) {
        return values == null ? List.of() : values;
    }
}
