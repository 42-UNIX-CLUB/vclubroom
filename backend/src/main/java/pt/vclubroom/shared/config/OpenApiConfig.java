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
        `:write` for changes.""";

    @Bean
    OpenAPI clubroomOpenApi() {
        return new OpenAPI()
            .info(new Info().title("Virtual Clubroom API").description(DESCRIPTION).version("v1"))
            .components(new Components()
                .addSecuritySchemes("session", new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.COOKIE)
                    .name("SESSION").description("Set by logging in to the app"))
                .addSecuritySchemes("csrf", new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER)
                    .name("X-XSRF-TOKEN").description("The XSRF-TOKEN cookie's value, on POST, PUT, PATCH and DELETE"))
                .addSecuritySchemes("bearer", new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer")
                    .bearerFormat("vcr_pat_…").description("A personal access token. Its scopes, one read and one write per area:\n\n"
                        + Arrays.stream(ApiArea.values())
                            .map(a -> "- `" + a.read() + "`, `" + a.write() + "`: " + a.description())
                            .collect(Collectors.joining("\n")))));
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
