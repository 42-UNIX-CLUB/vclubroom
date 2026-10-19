package pt.vclubroom.realtime.infrastructure;

import java.security.Principal;
import java.util.List;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import pt.vclubroom.realtime.spi.SubscriptionAuthorizer;
import pt.vclubroom.shared.security.AuthenticatedUser;

/**
 * Every inbound STOMP frame passes here. CONNECT needs a logged-in user; SEND is refused (push-only, R-03);
 * SUBSCRIBE needs an authorizer that knows the destination and says yes. Anything unknown is denied (fail closed).
 */
@Component
class SubscriptionAuthorizationInterceptor implements ChannelInterceptor {

    private final List<SubscriptionAuthorizer> authorizers;

    SubscriptionAuthorizationInterceptor(List<SubscriptionAuthorizer> authorizers) {
        this.authorizers = authorizers;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        AuthenticatedUser user = authenticatedUser(accessor.getUser());

        if (command == StompCommand.CONNECT && user == null) {
            throw new MessageDeliveryException("Unauthenticated");
        }
        if (command == StompCommand.SEND) {
            throw new MessageDeliveryException("Client SEND is not supported; use the REST API");
        }
        if (command == StompCommand.SUBSCRIBE) {
            String destination = accessor.getDestination();
            if (user == null || destination == null) {
                throw new MessageDeliveryException("Forbidden");
            }
            if (destination.startsWith("/user/queue/")) {
                return message;                         // resolved per session by Spring: private by construction
            }
            boolean allowed = authorizers.stream()
                .filter(a -> a.supports(destination))
                .findFirst()
                .map(a -> a.canSubscribe(user.actor(), destination))
                .orElse(false);
            if (!allowed) {
                throw new MessageDeliveryException("Forbidden");
            }
        }
        return message;
    }

    private static AuthenticatedUser authenticatedUser(Principal principal) {
        if (principal instanceof Authentication auth && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        return null;
    }
}
