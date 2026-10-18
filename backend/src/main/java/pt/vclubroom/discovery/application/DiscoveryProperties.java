package pt.vclubroom.discovery.application;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The club categories from app.clubs.categories in application.yml (R-20). The club module binds the same list
 * to validate new clubs; discovery reads its own copy because it may only use club's api and events.
 */
@ConfigurationProperties("app.clubs")
public record DiscoveryProperties(List<String> categories) {

    public DiscoveryProperties {
        categories = categories == null ? List.of() : List.copyOf(categories);
    }
}
