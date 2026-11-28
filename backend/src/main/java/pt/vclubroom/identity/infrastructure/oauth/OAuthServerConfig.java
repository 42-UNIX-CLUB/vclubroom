package pt.vclubroom.identity.infrastructure.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.jackson2.OAuth2AuthorizationServerJackson2Module;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Spring Authorization Server (E20-S04, E20-S05): apps send people to /oauth2/authorize, people log in to the app as
 * usual and consent on /oauth/consent (the SPA, answering through ConsentAnswerFilter), then apps exchange the code at
 * /oauth2/token. Its stores are in
 * PostgreSQL, so every backend instance shares them.
 */
@Configuration
class OAuthServerConfig {

    @Bean
    RegisteredClientRepository registeredClients(JdbcTemplate jdbc) {
        return new JdbcRegisteredClientRepository(jdbc);
    }

    /** Who signed in to an app is kept as JSON: with our principals among the classes read back (PrincipalMixins). */
    @Bean
    OAuth2AuthorizationService authorizations(JdbcTemplate jdbc, RegisteredClientRepository clients) {
        JdbcOAuth2AuthorizationService service = new JdbcOAuth2AuthorizationService(jdbc, clients);
        ObjectMapper json = new ObjectMapper();
        json.registerModules(SecurityJackson2Modules.getModules(OAuthServerConfig.class.getClassLoader()));
        json.registerModule(new OAuth2AuthorizationServerJackson2Module());
        json.registerModule(new PrincipalMixins());
        JdbcOAuth2AuthorizationService.OAuth2AuthorizationRowMapper rows = new JdbcOAuth2AuthorizationService.OAuth2AuthorizationRowMapper(clients);
        rows.setObjectMapper(json);
        JdbcOAuth2AuthorizationService.OAuth2AuthorizationParametersMapper parameters = new JdbcOAuth2AuthorizationService.OAuth2AuthorizationParametersMapper();
        parameters.setObjectMapper(json);
        service.setAuthorizationRowMapper(rows);
        service.setAuthorizationParametersMapper(parameters);
        return service;
    }

    @Bean
    OAuth2AuthorizationConsentService consents(JdbcTemplate jdbc, RegisteredClientRepository clients) {
        return new JdbcOAuth2AuthorizationConsentService(jdbc, clients);
    }

    @Bean
    AuthorizationServerSettings authorizationServerSettings(@Value("${app.public-url}") String publicUrl) {
        return AuthorizationServerSettings.builder().issuer(publicUrl).build();
    }

    /**
     * The authorization server's endpoints, before every other chain. They use the app's own login session; someone
     * not logged in is sent to the app's login page, which brings them back here.
     */
    @Bean
    @Order(0)
    SecurityFilterChain authorizationServerFilterChain(HttpSecurity http, SecurityContextRepository sessions, ObjectMapper json)
        throws Exception {
        OAuth2AuthorizationServerConfigurer server = OAuth2AuthorizationServerConfigurer.authorizationServer();
        http
            .securityMatcher(server.getEndpointsMatcher())
            .with(server, s -> s.authorizationEndpoint(endpoint -> endpoint.consentPage("/oauth/consent")))
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            .securityContext(ctx -> ctx.securityContextRepository(sessions))
            .requestCache(cache -> cache.requestCache(new NullRequestCache()))
            .addFilterAfter(new ConsentAnswerFilter(json), SecurityContextHolderFilter.class)
            .cors(cors -> cors.configurationSource(endpointCors()))
            .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(OAuthServerConfig::toLogin,
                new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));
        return http.build();
    }

    private static void toLogin(HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
        throws IOException {
        String back = request.getRequestURI() + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
        response.sendRedirect("/login?returnTo=" + URLEncoder.encode(back, StandardCharsets.UTF_8));
    }

    /** Single-page apps exchange codes and revoke tokens from their own origin, without cookies. */
    private static UrlBasedCorsConfigurationSource endpointCors() {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOriginPatterns(List.of("*"));
        cors.setAllowedMethods(List.of("POST"));
        cors.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE));
        cors.setAllowCredentials(false);
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/oauth2/token", cors);
        source.registerCorsConfiguration("/oauth2/revoke", cors);
        return source;
    }
}
