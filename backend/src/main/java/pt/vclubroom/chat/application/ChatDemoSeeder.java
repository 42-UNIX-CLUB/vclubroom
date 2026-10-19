package pt.vclubroom.chat.application;

import java.util.List;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import pt.vclubroom.chat.persistence.ChatMessageRepository;
import pt.vclubroom.club.api.ClubCatalog;
import pt.vclubroom.identity.api.UserDirectory;
import pt.vclubroom.shared.demo.DemoData;
import pt.vclubroom.shared.security.Actor;
import pt.vclubroom.space.api.ChannelType;
import pt.vclubroom.space.api.SpaceAccessPolicy;

/** A short conversation in Chess Club's Lounge. */
@Component
@Profile(DemoData.PROFILE)
@Order(DemoData.CHAT)
class ChatDemoSeeder implements ApplicationRunner {

    private record Line(String author, String body) { }

    private static final List<Line> LOUNGE = List.of(
        new Line("bob", "Who's up for blitz tonight?"),
        new Line("carol", "Me! 3+2?"),
        new Line("dave", "Count me in, after 19h"),
        new Line("bob", "Cluster 2 then"),
        new Line("carol", "Bringing the clocks"));

    private final ChatService chat;
    private final ChatMessageRepository messages;
    private final SpaceAccessPolicy spaceAccess;
    private final ClubCatalog clubs;
    private final UserDirectory users;

    ChatDemoSeeder(ChatService chat, ChatMessageRepository messages, SpaceAccessPolicy spaceAccess, ClubCatalog clubs,
                   UserDirectory users) {
        this.chat = chat;
        this.messages = messages;
        this.spaceAccess = spaceAccess;
        this.clubs = clubs;
        this.users = users;
    }

    @Override
    public void run(ApplicationArguments args) {
        UUID clubId = clubs.findBySlug("chess-club").orElseThrow().id();
        Actor admin = new Actor(userId("alice"), true);
        DemoData.awaitUntil(() -> !spaceAccess.readableChannels(clubId, admin, ChannelType.CHAT).isEmpty(), "Chess Club's Lounge");
        UUID lounge = spaceAccess.readableChannels(clubId, admin, ChannelType.CHAT).iterator().next();
        if (!messages.findPage(lounge, Long.MAX_VALUE, Limit.of(1)).isEmpty()) {
            return;
        }
        for (Line line : LOUNGE) {
            chat.send(new Actor(userId(line.author()), false), lounge, new SendMessageCommand(line.body(), List.of(), UUID.randomUUID()));
        }
    }

    private UUID userId(String login) {
        return users.findByLogin(login).orElseThrow().id();
    }
}
