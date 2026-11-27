package pt.vclubroom.identity.application;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.shared.error.NotFoundException;
import pt.vclubroom.shared.security.ApiScopes;

/**
 * The apps people let in (E20-S05): what an app asks for at consent, the apps someone authorized, and taking that back.
 * Reads the authorization server's tables, where principal_name is the user's id.
 */
@Service
public class AuthorizedAppService {

    private final JdbcTemplate jdbc;

    AuthorizedAppService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** For the consent page: the app behind clientId, and the scopes it asks for that exist (others are ignored). */
    @Transactional(readOnly = true)
    public ConsentRequestView consentRequest(UUID userId, String clientId, String scope) {
        Set<String> asked = new LinkedHashSet<>(ApiScopes.all());
        asked.retainAll(Set.of(scope == null ? new String[0] : scope.trim().split("\\s+")));
        return jdbc.query("""
                select rc.id, rc.client_id, rc.client_name, a.description, a.homepage_url, u.display_name,
                       (select c.authorities from public.oauth2_authorization_consent c
                        where c.registered_client_id = rc.id and c.principal_name = ?) as granted
                from public.oauth2_registered_client rc
                join identity.oauth_apps a on a.id = rc.id
                join identity.users u on u.id = a.owner_id
                where rc.client_id = ?
                """, (rs, row) -> new ConsentRequestView(rs.getString("client_id"), rs.getString("client_name"),
                rs.getString("description"), rs.getString("homepage_url"), rs.getString("display_name"),
                List.copyOf(asked), scopes(rs.getString("granted"))), userId.toString(), clientId)
            .stream().findFirst()
            .orElseThrow(() -> new NotFoundException("APP_NOT_FOUND", "No such app."));
    }

    @Transactional(readOnly = true)
    public List<AuthorizedAppView> list(UUID userId) {
        return jdbc.query("""
            select rc.id, rc.client_name, a.homepage_url, c.authorities
            from public.oauth2_authorization_consent c
            join public.oauth2_registered_client rc on rc.id = c.registered_client_id
            left join identity.oauth_apps a on a.id = rc.id
            where c.principal_name = ?
            order by rc.client_name
            """, (rs, row) -> new AuthorizedAppView(rs.getString("id"), rs.getString("client_name"),
            rs.getString("homepage_url"), scopes(rs.getString("authorities"))), userId.toString());
    }

    /** The app loses access: its tokens stop working, and it must ask again. */
    @Transactional
    public void revoke(UUID userId, String appId) {
        int consents = jdbc.update("delete from public.oauth2_authorization_consent where registered_client_id = ? and principal_name = ?",
            appId, userId.toString());
        int authorizations = jdbc.update("delete from public.oauth2_authorization where registered_client_id = ? and principal_name = ?",
            appId, userId.toString());
        if (consents + authorizations == 0) {
            throw new NotFoundException("APP_NOT_FOUND", "This app has no access to your account.");
        }
    }

    /** When the account is disabled. */
    @Transactional
    public void revokeAll(UUID userId) {
        jdbc.update("delete from public.oauth2_authorization_consent where principal_name = ?", userId.toString());
        jdbc.update("delete from public.oauth2_authorization where principal_name = ?", userId.toString());
    }

    /** "SCOPE_profile:read,SCOPE_forum:read" → known scopes, in the catalogue's order. */
    private static List<String> scopes(String authorities) {
        if (authorities == null) {
            return List.of();
        }
        Set<String> granted = new LinkedHashSet<>(Arrays.stream(authorities.split(",")).map(a -> a.replaceFirst("^SCOPE_", "")).toList());
        return ApiScopes.all().stream().filter(granted::contains).toList();
    }
}
