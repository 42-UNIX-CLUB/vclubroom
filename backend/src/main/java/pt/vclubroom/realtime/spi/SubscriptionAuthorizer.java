package pt.vclubroom.realtime.spi;

import pt.vclubroom.shared.security.Actor;

/** Implemented by the modules that own topics. The first authorizer whose supports() matches decides. */
public interface SubscriptionAuthorizer {

    boolean supports(String destination);

    boolean canSubscribe(Actor actor, String destination);
}
