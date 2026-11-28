package pt.vclubroom.identity.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig {

    /** API and WebSocket calls get a 401 instead of a redirect to the 42 login page. */
    private static final RequestMatcher API_OR_WS = request -> {
        String uri = request.getRequestURI();
        return uri.startsWith("/api/") || uri.equals("/ws") || uri.startsWith("/ws/");
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            FortyTwoOAuth2UserService fortyTwoUserService,
                                            SecurityContextRepository securityContextRepository) throws Exception {
        http
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
