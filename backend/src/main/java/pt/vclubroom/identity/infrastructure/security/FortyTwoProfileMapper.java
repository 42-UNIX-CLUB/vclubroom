package pt.vclubroom.identity.infrastructure.security;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import pt.vclubroom.identity.domain.FortyTwoProfile;

/**
 * Translates the raw /v2/me attribute map into our FortyTwoProfile.
 * Anti-corruption layer: 42's JSON shape never leaks past this class.
 */
@Component
class FortyTwoProfileMapper {

    FortyTwoProfile map(Map<String, Object> a) {
        long ftId = ((Number) a.get("id")).longValue();
        String login = (String) a.get("login");
        String displayName = a.get("displayname") instanceof String s && !s.isBlank() ? s : login;
        return new FortyTwoProfile(
            ftId,
            login,
            (String) a.get("email"),
            displayName,
            avatar(a.get("image")),
            parseInt(a.get("pool_year")),
            (String) a.get("pool_month"),
            Boolean.TRUE.equals(a.get("alumni?")),
            Boolean.TRUE.equals(a.get("staff?")),
            campusNames(a.get("campus")),
            cursusSlugs(a.get("cursus_users")));
    }

    private static String avatar(Object image) {
        if (image instanceof Map<?, ?> img) {
            if (img.get("versions") instanceof Map<?, ?> versions && versions.get("medium") instanceof String medium) {
                return medium;
            }
            if (img.get("link") instanceof String link) {
                return link;
            }
        }
        return null;
    }

    private static Integer parseInt(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            return Integer.valueOf(s.trim());
        }
        return null;
    }

    private static List<String> campusNames(Object campus) {
        if (!(campus instanceof Collection<?> list)) {
            return List.of();
        }
        return list.stream()
            .filter(Map.class::isInstance)
            .map(m -> ((Map<?, ?>) m).get("name"))
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .toList();
    }

    private static List<String> cursusSlugs(Object cursusUsers) {
        if (!(cursusUsers instanceof Collection<?> list)) {
            return List.of();
        }
        return list.stream()
            .filter(Map.class::isInstance)
            .map(cu -> ((Map<?, ?>) cu).get("cursus"))
            .filter(Map.class::isInstance)
            .map(c -> ((Map<?, ?>) c).get("slug"))
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .toList();
    }
}
