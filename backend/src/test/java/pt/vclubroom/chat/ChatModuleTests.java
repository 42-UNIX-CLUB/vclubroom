package pt.vclubroom.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Limit;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pt.vclubroom.TestcontainersConfiguration;
import pt.vclubroom.chat.application.ChatMessageView;
import pt.vclubroom.chat.application.ChatService;
import pt.vclubroom.chat.application.SendMessageCommand;
import pt.vclubroom.chat.persistence.ChatMessageRepository;
import pt.vclubroom.identity.api.UserDirectory;
import pt.vclubroom.media.api.MediaCatalog;
import pt.vclubroom.realtime.api.RealtimePublisher;
import pt.vclubroom.shared.security.Actor;
import pt.vclubroom.space.api.ChannelAccess;
import pt.vclubroom.space.api.ChannelType;
import pt.vclubroom.space.api.SpaceAccessPolicy;

/** The chat module on its own (E10-S02): the space, media, identity and realtime APIs are mocked. */
@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
class ChatModuleTests {

    @MockitoBean
    SpaceAccessPolicy spaceAccess;

    @MockitoBean
    MediaCatalog mediaCatalog;

    @MockitoBean
    UserDirectory users;

    @MockitoBean
    RealtimePublisher realtime;

    @Autowired
    ChatService chat;

    @Autowired
    ChatMessageRepository messages;

    @Test
    void aRetriedSendIsTheSameMessagePushedOnce() {
        UUID channelId = UUID.randomUUID();
        given(spaceAccess.forChannel(any(), any())).willReturn(
            new ChannelAccess(channelId, ChannelType.CHAT, UUID.randomUUID(), UUID.randomUUID(), 1, true, true, false));
        given(users.findByIds(anyCollection())).willReturn(Map.of());
        given(mediaCatalog.resolve(anyCollection())).willReturn(Map.of());
        Actor sam = new Actor(UUID.randomUUID(), false);
        UUID nonce = UUID.randomUUID();

        ChatMessageView first = chat.send(sam, channelId, new SendMessageCommand("gg", List.of(), nonce));
        ChatMessageView retry = chat.send(sam, channelId, new SendMessageCommand("gg", List.of(), nonce));

        assertThat(retry.id()).isEqualTo(first.id());
        assertThat(messages.findPage(channelId, Long.MAX_VALUE, Limit.of(10))).hasSize(1);
        verify(realtime, times(1)).toTopic(eq("/topic/channels/" + channelId + "/messages"), eq("message.created"), any());
    }
}
