package pt.vclubroom.identity;

import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.admin")
record AdminProperties(Set<String> bootstrapLogins) {

    AdminProperties {
        bootstrapLogins = bootstrapLogins == null
            ? Set.of()
            : bootstrapLogins.stream().map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }
}
