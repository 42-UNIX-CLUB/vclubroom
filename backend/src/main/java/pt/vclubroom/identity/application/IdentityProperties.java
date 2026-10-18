package pt.vclubroom.identity.application;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** app.admin.* in application.yml (values from APP_ADMIN_BOOTSTRAP_LOGINS and APP_BOOTSTRAP_ADMIN_*). */
@ConfigurationProperties("app.admin")
public record IdentityProperties(String bootstrapLogins,
                                 String bootstrapLocalUsername,
                                 String bootstrapLocalPassword) {

    /** 42 logins that become ADMIN at their first login (R-09), lower-cased. */
    public Set<String> bootstrapLoginSet() {
        if (bootstrapLogins == null || bootstrapLogins.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(bootstrapLogins.split(","))
            .map(String::trim).map(String::toLowerCase).filter(s -> !s.isEmpty())
            .collect(Collectors.toUnmodifiableSet());
    }
}
