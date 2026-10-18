package pt.vclubroom.club.application;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import pt.vclubroom.shared.error.InvalidRequestException;

/** app.clubs.* in application.yml: the fixed category list (R-20) and the pending-request limit. */
@ConfigurationProperties("app.clubs")
public record ClubProperties(List<String> categories,
                             @DefaultValue("3") int maxPendingRequestsPerUser) {

    public void requireValidCategory(String category) {
        if (category != null && !categories.contains(category)) {
            throw new InvalidRequestException("INVALID_CATEGORY", "Unknown category: " + category);
        }
    }
}