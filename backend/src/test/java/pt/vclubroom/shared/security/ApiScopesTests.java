package pt.vclubroom.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Which scope each path needs with a token (E20-S01). */
class ApiScopesTests {

    @Test
    void eachAreaReadsWithGetAndWritesWithAnythingElse() {
        assertThat(ApiScopes.required("GET", "/api/me")).contains("profile:read");
        assertThat(ApiScopes.required("GET", "/api/users/42")).contains("profile:read");
        assertThat(ApiScopes.required("POST", "/api/clubs")).contains("clubs:write");
        assertThat(ApiScopes.required("GET", "/api/me/memberships")).contains("clubs:read");
        assertThat(ApiScopes.required("PATCH", "/api/spaces/7")).contains("clubs:write");
        assertThat(ApiScopes.required("GET", "/api/clubs/7/feed")).contains("forum:read");
        assertThat(ApiScopes.required("POST", "/api/channels/7/posts")).contains("forum:write");
        assertThat(ApiScopes.required("PUT", "/api/posts/7/reactions/like")).contains("forum:write");
        assertThat(ApiScopes.required("GET", "/api/channels/7/messages")).contains("chat:read");
        assertThat(ApiScopes.required("DELETE", "/api/messages/7")).contains("chat:write");
        assertThat(ApiScopes.required("GET", "/api/clubs/7/events")).contains("events:read");
        assertThat(ApiScopes.required("PUT", "/api/events/7/rsvp")).contains("events:write");
        assertThat(ApiScopes.required("GET", "/api/me/calendar-feed")).contains("events:read");
        assertThat(ApiScopes.required("HEAD", "/api/notifications")).contains("notifications:read");
        assertThat(ApiScopes.required("PUT", "/api/me/notification-preferences")).contains("notifications:write");
        assertThat(ApiScopes.required("POST", "/api/support/tickets")).contains("support:write");
        assertThat(ApiScopes.required("POST", "/api/media/uploads")).contains("media:write");
    }

    @Test
    void someThingsAreNeverForTokens() {
        assertThat(ApiScopes.required("GET", "/api/admin/users")).isEmpty();
        assertThat(ApiScopes.required("POST", "/api/auth/login")).isEmpty();
        assertThat(ApiScopes.required("PUT", "/api/me/password")).isEmpty();
        assertThat(ApiScopes.required("GET", "/api/me/tokens")).isEmpty();
        assertThat(ApiScopes.required("GET", "/api/brand-new")).isEmpty();        // until it is given an area
    }

    @Test
    void listsEveryScope() {
        assertThat(ApiScopes.all()).hasSize(16).startsWith("profile:read", "profile:write").contains("media:write");
    }
}
