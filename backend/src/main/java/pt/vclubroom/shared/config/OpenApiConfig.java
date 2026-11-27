package pt.vclubroom.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pt.vclubroom.shared.security.ApiArea;
import pt.vclubroom.shared.security.ApiScopes;
import pt.vclubroom.shared.security.RateLimitProperties;
import pt.vclubroom.shared.security.RateLimitProperties.Limit;

/** The API's documentation (/v3/api-docs, /swagger-ui.html): public in production, without the admin endpoints (E19-S01). */
@Configuration
class OpenApiConfig {

    private static final String DESCRIPTION = """
        The REST API of Virtual Clubroom, the 42 clubs platform. Endpoints under `/api/public` need no login.
        The others use the session of someone logged in to the app: the `SESSION` cookie, and for any change the
        `X-XSRF-TOKEN` header echoing the `XSRF-TOKEN` cookie (first `GET /api/auth/csrf`). Logged in to the app in this
        browser, **Try it out** works as you. Errors are RFC 9457 problem details with a stable `code`.

        Scripts and apps use a token instead (E20): `Authorization: Bearer vcr_pat_…`, created in Settings › Developer
        tokens, with neither cookie nor CSRF. Each endpoint names the scope a token needs: its area, `:read` for GET,
        `:write` for changes.

        Apps that people sign in to are registered in Settings › Your apps, and use OAuth 2's authorization code with
        PKCE (`S256`): send people to `/oauth2/authorize` with the scopes the app needs; they log in and consent; the
        app exchanges the code at `/oauth2/token`. Their access tokens last an hour and are sent like personal ones.
        Confidential apps also get a refresh token, valid 30 days and replaced at each use, and can revoke tokens at
        `/oauth2/revoke`. The server describes itself at `/.well-known/oauth-authorization-server`.""";

    @Bean
    OpenAPI clubroomOpenApi(RateLimitProperties limits) {
        return new OpenAPI()
            .info(new Info().title("Virtual Clubroom API").description(DESCRIPTION + "\n\n" + rateLimits(limits)).version("v1"))
            .components(new Components()
                .addSecuritySchemes("session", new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.COOKIE)
                    .name("SESSION").description("Set by logging in to the app"))
                .addSecuritySchemes("csrf", new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER)
                    .name("X-XSRF-TOKEN").description("The XSRF-TOKEN cookie's value, on POST, PUT, PATCH and DELETE"))
                .addSecuritySchemes("bearer", new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer")
                    .bearerFormat("vcr_pat_…").description("A personal access token, or an app's access token. The scopes, one read and one write per area:\n\n"
                        + Arrays.stream(ApiArea.values())
                            .map(a -> "- `" + a.read() + "`, `" + a.write() + "`: " + a.description())
                            .collect(Collectors.joining("\n")))));
    }

    /** The limits of E20-S03, from the configuration they are applied with. */
    private static String rateLimits(RateLimitProperties limits) {
        return "**Rate limits.** Each token may make " + bucket(limits.token()) + "; its changes (POST, PUT, PATCH, DELETE) "
            + bucket(limits.tokenWrites()) + "; all the tokens of one person together " + bucket(limits.user())
            + ". Without logging in, `/api/public` allows " + bucket(limits.anonymous()) + " per IP. Limited responses say "
            + "where you stand: `RateLimit-Limit`, `RateLimit-Remaining`, and `RateLimit-Reset` (seconds until full). Past a "
            + "limit: `429`, code `RATE_LIMITED`, and `Retry-After` in seconds. Browser sessions aren't limited this way.";
    }

    private static String bucket(Limit limit) {
        return limit.burst() + " requests at once, refilled at " + limit.perHour() + " an hour";
    }

    /** Each operation: the session (and CSRF for changes), or a token with the scope its path needs (E20-S01). */
    @Bean
    OpenApiCustomizer tokenScopes() {
        return api -> api.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) ->
            ApiScopes.required(method.name(), path).ifPresent(scope -> {
                SecurityRequirement session = method == PathItem.HttpMethod.GET || method == PathItem.HttpMethod.HEAD
                    ? new SecurityRequirement().addList("session")
                    : new SecurityRequirement().addList("session").addList("csrf");
                operation.setSecurity(List.of(session, new SecurityRequirement().addList("bearer", scope)));
                String note = "Token scope: `" + scope + "`.";
                operation.setDescription(operation.getDescription() == null ? note : operation.getDescription() + "\n\n" + note);
            })));
    }
}
