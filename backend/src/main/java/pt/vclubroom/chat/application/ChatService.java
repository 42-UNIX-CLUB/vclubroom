package pt.vclubroom.chat.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.vclubroom.chat.domain.ChatMessage;
import pt.vclubroom.chat.persistence.ChatMessageRepository;
import pt.vclubroom.media.api.MediaCatalog;
import pt.vclubroom.media.api.MediaPurpose;
import pt.vclubroom.realtime.api.RealtimePublisher;
import pt.vclubroom.shared.error.BusinessRuleException;
import pt.vclubroom.shared.error.ForbiddenException;
import pt.vclubroom.shared.error.NotFoundException;
import pt.vclubroom.shared.security.Actor;
import pt.vclubroom.space.api.ChannelAccess;
import pt.vclubroom.space.api.ChannelType;
import pt.vclubroom.space.api.SpaceAccessPolicy;

/**
 * Chat (chat.md §7): sending, history, edit and delete. Access is the space module's, as for the forum: read to
 * follow a room, write to talk, and only in the current era (BR-15). Every change is pushed after the commit.
 */
@Service
public class ChatService {

    private final ChatMessageRepository messages;
    private final SpaceAccessPolicy spaceAccess;
    private final MediaCatalog mediaCatalog;
    private final ChatMessageAssembler assembler;
    private final RealtimePublisher realtime;
    private final Clock clock;

    ChatService(ChatMessageRepository messages, SpaceAccessPolicy spaceAccess, MediaCatalog mediaCatalog,
                ChatMessageAssembler assembler, RealtimePublisher realtime, Clock clock) {
        this.messages = messages;
        this.spaceAccess = spaceAccess;
        this.mediaCatalog = mediaCatalog;
        this.assembler = assembler;
        this.realtime = realtime;
        this.clock = clock;
    }

    static String topic(UUID channelId) {
        return "/topic/channels/" + channelId + "/messages";
    }

    @Transactional
    public ChatMessageView send(Actor actor, UUID channelId, SendMessageCommand cmd) {
        ChannelAccess access = spaceAccess.forChannel(channelId, actor).requireType(ChannelType.CHAT).requireWrite();
        var existing = messages.findByChannelIdAndAuthorIdAndClientNonce(channelId, actor.userId(), cmd.clientNonce());
        if (existing.isPresent()) {
            return assembler.toView(existing.get());                     // a retry: the same message, not a new one
        }
        List<UUID> mediaIds = cmd.mediaIds() == null ? List.of() : cmd.mediaIds();
        ChatMessage message = ChatMessage.post(channelId, access.spaceId(), access.clubId(), access.currentEra(),
            actor.userId(), cmd.body(), mediaIds, cmd.clientNonce(), clock.instant());       // EMPTY_MESSAGE
        mediaCatalog.attach(mediaIds, actor.userId(), MediaPurpose.CHAT_ATTACHMENT);          // BR-19
        messages.save(message);                                           // IDENTITY: the id is assigned now
        ChatMessageView view = assembler.toView(message);
        realtime.toTopic(topic(channelId), "message.created", view);
        return view;
    }

    /** Oldest first for display; era = null for every era. */
    @Transactional(readOnly = true)
    public ChatPage history(Actor actor, UUID channelId, Integer era, Long before, int limit) {
        spaceAccess.forChannel(channelId, actor).requireType(ChannelType.CHAT).requireRead();
        long cursor = before == null ? Long.MAX_VALUE : before;
        Limit oneMore = Limit.of(limit + 1);
        List<ChatMessage> rows = era == null
            ? messages.findPage(channelId, cursor, oneMore)
            : messages.findPageInEra(channelId, era, cursor, oneMore);
        List<ChatMessage> page = rows.size() > limit ? rows.subList(0, limit) : rows;
        Long nextBefore = rows.size() > limit ? page.getLast().id() : null;
        return new ChatPage(assembler.toViews(page.reversed()), nextBefore);
    }

    /** The author, in the current era of a room they may still write in. */
    @Transactional
    public ChatMessageView edit(Actor actor, long messageId, String body) {
        ChatMessage message = load(messageId);
        ChannelAccess access = spaceAccess.forChannel(message.channelId(), actor).requireWrite();
        requireCurrentEra(access, message);
        message.edit(actor.userId(), body, clock.instant());                                   // CANNOT_EDIT
        ChatMessageView view = assembler.toView(message);
        realtime.toTopic(topic(message.channelId()), "message.updated", view);
        return view;
    }

    /** The author, or a moderator of the space; never in an earlier era. */
    @Transactional
    public ChatMessageView delete(Actor actor, long messageId) {
        ChatMessage message = load(messageId);
        ChannelAccess access = spaceAccess.forChannel(message.channelId(), actor).requireRead();
        boolean author = message.authorId().equals(actor.userId()) && access.canWrite();
        if (!author && !access.canModerate()) {
            throw new ForbiddenException("CANNOT_DELETE", "You cannot delete this message.");
        }
        requireCurrentEra(access, message);
        message.softDelete(actor.userId(), clock.instant());
        ChatMessageView view = assembler.toView(message);
        realtime.toTopic(topic(message.channelId()), "message.deleted", view);
        return view;
    }

    private ChatMessage load(long messageId) {
        return messages.findById(messageId)
            .orElseThrow(() -> new NotFoundException("MESSAGE_NOT_FOUND", "Message not found"));
    }

    private static void requireCurrentEra(ChannelAccess access, ChatMessage message) {
        if (!access.isCurrentEra(message.era())) {                                            // BR-15
            throw new BusinessRuleException("HISTORY_READ_ONLY", "Messages from previous eras are read-only.");
        }
    }
}
