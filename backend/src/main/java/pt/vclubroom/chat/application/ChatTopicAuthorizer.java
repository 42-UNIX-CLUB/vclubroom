package pt.vclubroom.chat.application;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import pt.vclubroom.realtime.spi.SubscriptionAuthorizer;
import pt.vclubroom.shared.error.NotFoundException;
import pt.vclubroom.shared.security.Actor;
import pt.vclubroom.space.api.ChannelType;
import pt.vclubroom.space.api.SpaceAccessPolicy;

/** Realtime SPI: only readers of a chat channel may follow it. */
@Component
class ChatTopicAuthorizer implements SubscriptionAuthorizer {

    private static final Pattern MESSAGES = Pattern.compile("^/topic/channels/([0-9a-f-]{36})/messages$");

    private final SpaceAccessPolicy spaceAccess;

    ChatTopicAuthorizer(SpaceAccessPolicy spaceAccess) {
        this.spaceAccess = spaceAccess;
    }

    @Override
    public boolean supports(String destination) {
        return MESSAGES.matcher(destination).matches();
    }

    @Override
    public boolean canSubscribe(Actor actor, String destination) {
        Matcher m = MESSAGES.matcher(destination);
        if (!m.matches()) {
            return false;
        }
        try {
            var access = spaceAccess.forChannel(UUID.fromString(m.group(1)), actor);
            return access.type() == ChannelType.CHAT && access.canRead();
        } catch (NotFoundException | IllegalArgumentException e) {
            return false;
        }
    }
}
