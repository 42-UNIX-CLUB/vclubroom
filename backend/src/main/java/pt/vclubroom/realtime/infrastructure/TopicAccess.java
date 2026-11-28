package pt.vclubroom.realtime.infrastructure;

import java.util.List;
import org.springframework.stereotype.Component;
import pt.vclubroom.realtime.spi.SubscriptionAuthorizer;
import pt.vclubroom.shared.security.Actor;

/** Who may read a topic: the first authorizer that knows the destination decides; unknown destinations are denied. */
@Component
class TopicAccess {

    private final List<SubscriptionAuthorizer> authorizers;

    TopicAccess(List<SubscriptionAuthorizer> authorizers) {
        this.authorizers = authorizers;
    }

    boolean canRead(Actor actor, String destination) {
        return authorizers.stream()
            .filter(a -> a.supports(destination))
            .findFirst()
            .map(a -> a.canSubscribe(actor, destination))
            .orElse(false);
    }
}
