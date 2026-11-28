package pt.vclubroom.identity.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import pt.vclubroom.identity.application.BearerTokens;
import pt.vclubroom.shared.security.RateLimitProperties;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig {

    /** API and WebSocket calls get a 401 instead of a redirect to the 42 login page. */
    private static final RequestMatcher API_OR_WS = request -> {
        String uri = request.getRequestURI();
        return uri.startsWith("/api/") || uri.equals("/ws") || uri.startsWith("/ws/");
    };

    /**
     * A request whose credential is an Authorization: Bearer header. A browser never adds that header by itself, so a
     * forged cross-site request can't carry one: these are the only requests exempt from CSRF.
     */
    private static final RequestMatcher BEARER = request -> {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        return authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7);
    };

    /**
     * API calls made with a token (E20): Authorization: Bearer, or a browser app's CORS preflight for one. Tokens are
     * never sent by the browser on its own, so these need neither a session nor CSRF, and any origin may call.
     */
    private static final RequestMatcher TOKEN_API = request -> {
        if (!request.getRequestURI().startsWith("/api/")) {
            return false;
        }
        if (request.getHeader(HttpHeaders.AUTHORIZATION) != null) {
            return BEARER.matches(request);
        }
        String preflightHeaders = request.getHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS);
        return HttpMethod.OPTIONS.matches(request.getMethod()) && preflightHeaders != null
            && preflightHeaders.toLowerCase().contains("authorization");
    };

    /** Token requests, before the session chain: each needs the scope of its path (shared.security.ApiScopes). */
    @Bean
    @Order(1)
    SecurityFilterChain tokenFilterChain(HttpSecurity http, List<BearerTokens> tokens, RateLimiter limiter,
                                         RateLimitProperties limits, ObjectMapper json) throws Exception {
        TokenErrors errors = new TokenErrors(json);
        http
            .securityMatcher(TOKEN_API)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/public/**").permitAll()
                .anyRequest().access(new ScopeAuthorizationManager()))
            .addFilterBefore(new BearerTokenFilter(tokens, errors), AuthorizationFilter.class)
            .addFilterAfter(RateLimitFilter.forTokens(limiter, errors, limits), BearerTokenFilter.class)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .securityContext(ctx -> ctx.securityContextRepository(new RequestAttributeSecurityContextRepository()))
            .requestCache(cache -> cache.requestCache(new NullRequestCache()))
            .cors(cors -> cors.configurationSource(tokenCors()))
            // CSRF stays on, exempting only requests authenticated by their Bearer header (all of this chain's
            // changes today; preflights are OPTIONS, which CSRF never checks). If the chain ever accepted another
            // credential, such as a cookie, its requests would need a CSRF token again without anyone remembering.
            .csrf(csrf -> csrf.ignoringRequestMatchers(BEARER))
            .logout(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(errors::unauthenticated)
                .accessDeniedHandler(errors::forbidden));
        return http.build();
    }

    private static CorsConfigurationSource tokenCors() {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOriginPatterns(List.of("*"));
        cors.setAllowedMethods(List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE"));
        cors.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE));
        cors.setExposedHeaders(List.of(HttpHeaders.LOCATION, HttpHeaders.WWW_AUTHENTICATE, HttpHeaders.RETRY_AFTER,
            "RateLimit-Limit", "RateLimit-Remaining", "RateLimit-Reset"));
        cors.setAllowCredentials(false);
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }

    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            FortyTwoOAuth2UserService fortyTwoUserService,
                                            SecurityContextRepository securityContextRepository,
                                            RateLimiter limiter, RateLimitProperties limits, ObjectMapper json) throws Exception {
        http
            .addFilterAfter(RateLimitFilter.forAnonymous(limiter, new TokenErrors(json), limits), AnonymousAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/public/**", "/api/auth/login", "/api/auth/csrf").permitAll()
                .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                .requestMatchers("/ical/**").permitAll()                         // token is the credential
                .requestMatchers("/error", "/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll() // public docs (E19-S01)
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/**", "/ws", "/ws/**").authenticated()
                .anyRequest().denyAll())
            .securityContext(ctx -> ctx.securityContextRepository(securityContextRepository))
            .requestCache(cache -> cache.requestCache(new NullRequestCache()))
            // SPA-friendly CSRF: the XSRF-TOKEN cookie is readable by the frontend and echoed in X-XSRF-TOKEN.
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
            .oauth2Login(oauth -> oauth
                .userInfoEndpoint(userInfo -> userInfo.userService(fortyTwoUserService))
                .defaultSuccessUrl("/", true)
                .failureUrl("/login?error"))
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")                                   // POST + CSRF
                .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
                .deleteCookies("SESSION"))
            .exceptionHandling(ex -> ex
                .defaultAuthenticationEntryPointFor(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), API_OR_WS))
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable);
        return http.build();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();     // backed by Spring Session → Redis
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();   // {bcrypt} by default
    }

    /** Checks local username/password logins (AuthController). 42 logins go through oauth2Login above. */
    @Bean
    AuthenticationManager localAuthenticationManager(LocalUserDetailsService userDetailsService,
                                                     PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }
}
