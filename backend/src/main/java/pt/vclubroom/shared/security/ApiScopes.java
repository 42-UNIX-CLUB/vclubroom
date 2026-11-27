package pt.vclubroom.shared.security;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * The scope an API request needs when it comes with a token (E20-S01): the area of its path, read for GET and HEAD,
 * write for anything else. A path without an area is refused to tokens: the admin console, logging in, passwords,
 * the management of tokens and apps themselves, and any new endpoint until it is given an area here.
 */
public final class ApiScopes {

    /** Never with a token, whatever its scopes. */
    private static final List<PathPattern> NOT_FOR_TOKENS = patterns(
        "/api/admin/**", "/api/auth/**", "/api/me/password", "/api/me/tokens/**", "/api/me/apps/**",
        "/api/me/authorized-apps/**", "/api/oauth/**");

    /** The first match wins: specific paths before the general ones. */
    private static final List<Map.Entry<PathPattern, ApiArea>> AREAS = Stream.of(
            area(ApiArea.NOTIFICATIONS, "/api/me/notification-preferences", "/api/notifications/**"),
            area(ApiArea.EVENTS, "/api/me/calendar-feed", "/api/clubs/{clubId}/events/**", "/api/events/**", "/api/calendar"),
            area(ApiArea.CLUBS, "/api/me/memberships", "/api/me/club-requests", "/api/me/ownership-invitations"),
            area(ApiArea.PROFILE, "/api/me", "/api/users/**"),
            area(ApiArea.FORUM, "/api/clubs/{clubId}/feed", "/api/channels/{channelId}/posts/**", "/api/posts/**",
                "/api/comments/**", "/api/reactions"),
            area(ApiArea.CHAT, "/api/channels/{channelId}/messages/**", "/api/messages/**"),
            area(ApiArea.CLUBS, "/api/clubs/**", "/api/spaces/**", "/api/channels/**", "/api/ownership-invitations/**",
                "/api/permissions", "/api/campus-groups"),
            area(ApiArea.SUPPORT, "/api/support/**"),
            area(ApiArea.MEDIA, "/api/media/**"))
        .flatMap(List::stream)
        .toList();

    private ApiScopes() {
    }

    /** The scope a token needs for this request, or empty when tokens can't make it. */
    public static Optional<String> required(String method, String path) {
        PathContainer container = PathContainer.parsePath(path);
        if (NOT_FOR_TOKENS.stream().anyMatch(p -> p.matches(container))) {
            return Optional.empty();
        }
        boolean read = "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
        return AREAS.stream()
            .filter(rule -> rule.getKey().matches(container))
            .findFirst()
            .map(rule -> read ? rule.getValue().read() : rule.getValue().write());
    }

    /** Every scope, in order: profile:read, profile:write, clubs:read… */
    public static List<String> all() {
        return Arrays.stream(ApiArea.values()).flatMap(a -> Stream.of(a.read(), a.write())).toList();
    }

    private static List<Map.Entry<PathPattern, ApiArea>> area(ApiArea area, String... paths) {
        return patterns(paths).stream().map(p -> Map.entry(p, area)).toList();
    }

    private static List<PathPattern> patterns(String... paths) {
        return Arrays.stream(paths).map(PathPatternParser.defaultInstance::parse).toList();
    }
}
