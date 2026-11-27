package pt.vclubroom.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The API's documentation (/v3/api-docs, /swagger-ui.html): public in production, without the admin endpoints (E19-S01). */
@Configuration
class OpenApiConfig {

    private static final String DESCRIPTION = """
        The REST API of Virtual Clubroom, the 42 clubs platform. Endpoints under `/api/public` need no login.
        The others use the session of someone logged in to the app: the `SESSION` cookie, and for any change the
        `X-XSRF-TOKEN` header echoing the `XSRF-TOKEN` cookie (first `GET /api/auth/csrf`). Logged in to the app in this
        browser, **Try it out** works as you. Errors are RFC 9457 problem details with a stable `code`.""";

    @Bean
    OpenAPI clubroomOpenApi() {
        return new OpenAPI()
            .info(new Info().title("Virtual Clubroom API").description(DESCRIPTION).version("v1"))
            .components(new Components()
                .addSecuritySchemes("session", new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.COOKIE)
                    .name("SESSION").description("Set by logging in to the app"))
                .addSecuritySchemes("csrf", new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER)
                    .name("X-XSRF-TOKEN").description("The XSRF-TOKEN cookie's value, on POST, PUT, PATCH and DELETE")));
    }
}
