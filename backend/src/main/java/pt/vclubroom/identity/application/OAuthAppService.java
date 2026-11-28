package pt.vclubroom.identity.application;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.shared.error.BusinessRuleException;
import pt.vclubroom.shared.error.InvalidRequestException;
import pt.vclubroom.shared.error.NotFoundException;
import pt.vclubroom.shared.security.ApiScopes;

/**
 * OAuth apps (E20-S04): anyone registers up to three, like 42's intra (D-52). The authorization server keeps each as a
 * registered client; identity.oauth_apps adds its owner and description.
 */
@Service
public class OAuthAppService {

    public static final int MAX_APPS = 3;
    private static final int MAX_REDIRECT_URIS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RegisteredClientRepository clients;
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    OAuthAppService(RegisteredClientRepository clients, JdbcTemplate jdbc, PasswordEncoder passwordEncoder, Clock clock) {
        this.clients = clients;
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /**
     * Confidential apps run on a server and get a secret; public ones (single-page, mobile) don't. Every app uses PKCE,
     * asks people's consent, and gets opaque access tokens for an hour; confidential ones also refresh them.
     */
    @Transactional
    public RegisteredApp register(UUID ownerId, AppDetails details, boolean confidential) {
        AppDetails valid = validate(details);
        Integer owned = jdbc.queryForObject("select count(*) from identity.oauth_apps where owner_id = ?", Integer.class, ownerId);
        if (owned != null && owned >= MAX_APPS) {
            throw new BusinessRuleException("APP_LIMIT_REACHED", "You already have " + MAX_APPS + " apps. Delete one to register another.");
        }
        Instant now = clock.instant();
        String secret = confidential ? "vcr_secret_" + random(32) : null;
        RegisteredClient.Builder client = RegisteredClient.withId(UUID.randomUUID().toString())
            .clientId("vcr_app_" + random(16))
            .clientIdIssuedAt(now)
            .clientName(valid.name())
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUris(uris -> uris.addAll(valid.redirectUris()))
            .scopes(scopes -> scopes.addAll(ApiScopes.all()))                // people choose among them, at consent
            .clientSettings(ClientSettings.builder().requireProofKey(true).requireAuthorizationConsent(true).build())
            .tokenSettings(TokenSettings.builder()
                .accessTokenFormat(OAuth2TokenFormat.REFERENCE)
                .accessTokenTimeToLive(Duration.ofHours(1))
                .authorizationCodeTimeToLive(Duration.ofMinutes(5))
                .refreshTokenTimeToLive(Duration.ofDays(30))
                .reuseRefreshTokens(false)                                   // each use replaces it
                .build());
        if (confidential) {
            client.clientSecret(passwordEncoder.encode(secret))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN);
        } else {
            client.clientAuthenticationMethod(ClientAuthenticationMethod.NONE);
        }
        RegisteredClient registered = client.build();
        clients.save(registered);
        jdbc.update("insert into identity.oauth_apps (id, owner_id, description, homepage_url, created_at) values (?, ?, ?, ?, ?)",
            registered.getId(), ownerId, valid.description(), valid.homepageUrl(), Timestamp.from(now));
        return new RegisteredApp(view(registered.getId()).orElseThrow(), secret);
    }

    @Transactional(readOnly = true)
    public List<AppView> mine(UUID ownerId) {
        return jdbc.queryForList("select id from identity.oauth_apps where owner_id = ? order by created_at", String.class, ownerId)
            .stream().map(id -> view(id).orElseThrow()).toList();
    }

    @Transactional
    public AppView update(UUID ownerId, String appId, AppDetails details) {
        AppDetails valid = validate(details);
        RegisteredClient client = owned(ownerId, appId);
        clients.save(RegisteredClient.from(client)
            .clientName(valid.name())
            .redirectUris(uris -> {
                uris.clear();
                uris.addAll(valid.redirectUris());
            })
            .build());
        jdbc.update("update identity.oauth_apps set description = ?, homepage_url = ? where id = ?",
            valid.description(), valid.homepageUrl(), appId);
        return view(appId).orElseThrow();
    }

    /** A confidential app's new secret; the old one stops working. */
    @Transactional
    public RegisteredApp newSecret(UUID ownerId, String appId) {
        RegisteredClient client = owned(ownerId, appId);
        if (client.getClientSecret() == null) {
            throw new BusinessRuleException("APP_IS_PUBLIC", "A public app has no secret: it uses PKCE alone.");
        }
        String secret = "vcr_secret_" + random(32);
        clients.save(RegisteredClient.from(client).clientSecret(passwordEncoder.encode(secret)).build());
        return new RegisteredApp(view(appId).orElseThrow(), secret);
    }

    /** The app is gone, and so is everyone's access through it. */
    @Transactional
    public void delete(UUID ownerId, String appId) {
        owned(ownerId, appId);
        jdbc.update("delete from public.oauth2_registered_client where id = ?", appId);      // and identity.oauth_apps
    }

    private RegisteredClient owned(UUID ownerId, String appId) {
        Boolean mine = jdbc.queryForObject("select exists (select 1 from identity.oauth_apps where id = ? and owner_id = ?)",
            Boolean.class, appId, ownerId);
        RegisteredClient client = Boolean.TRUE.equals(mine) ? clients.findById(appId) : null;
        if (client == null) {
            throw new NotFoundException("APP_NOT_FOUND", "No such app.");
        }
        return client;
    }

    private Optional<AppView> view(String appId) {
        RegisteredClient client = clients.findById(appId);
        if (client == null) {
            return Optional.empty();
        }
        return jdbc.query("select description, homepage_url, created_at from identity.oauth_apps where id = ?", (rs, row) ->
            new AppView(client.getId(), client.getClientId(), client.getClientName(), rs.getString("description"),
                rs.getString("homepage_url"), client.getRedirectUris().stream().sorted().toList(),
                client.getClientSecret() != null, rs.getTimestamp("created_at").toInstant()), appId).stream().findFirst();
    }

    private static AppDetails validate(AppDetails d) {
        String name = d.name() == null ? "" : d.name().strip();
        if (name.isEmpty() || name.length() > 80) {
            throw new InvalidRequestException("APP_NAME_INVALID", "Give the app a name of up to 80 characters.");
        }
        String description = d.description() == null || d.description().isBlank() ? null : d.description().strip();
        if (description != null && description.length() > 500) {
            throw new InvalidRequestException("APP_DESCRIPTION_INVALID", "Describe the app in up to 500 characters.");
        }
        String homepage = d.homepageUrl() == null || d.homepageUrl().isBlank() ? null : d.homepageUrl().strip();
        if (homepage != null && (homepage.length() > 300 || !webUrl(homepage))) {
            throw new InvalidRequestException("APP_HOMEPAGE_INVALID", "The homepage must be an https:// address.");
        }
        List<String> uris = d.redirectUris() == null ? List.of() : d.redirectUris().stream().map(String::strip)
            .filter(u -> !u.isEmpty()).distinct().toList();
        if (uris.isEmpty() || uris.size() > MAX_REDIRECT_URIS || !uris.stream().allMatch(OAuthAppService::redirectUri)) {
            throw new InvalidRequestException("APP_REDIRECT_URIS_INVALID",
                "Give one to " + MAX_REDIRECT_URIS + " redirect URIs: https://, http://localhost, or an app's own scheme like com.example.app:/callback.");
        }
        return new AppDetails(name, description, homepage, uris);
    }

    /** https://, or http:// on this computer for development. */
    private static boolean webUrl(String value) {
        try {
            URI uri = new URI(value);
            return uri.getHost() != null && ("https".equals(uri.getScheme())
                || "http".equals(uri.getScheme()) && Set.of("localhost", "127.0.0.1", "[::1]").contains(uri.getHost()));
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /** A web address as above, or a mobile app's private-use scheme (RFC 8252: reverse domain, with a dot); no fragment. */
    private static boolean redirectUri(String value) {
        try {
            URI uri = new URI(value);
            if (uri.getFragment() != null || uri.getScheme() == null || value.length() > 200) {
                return false;
            }
            return webUrl(value) || uri.getScheme().contains(".") && !uri.getScheme().startsWith("http");
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private static String random(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
