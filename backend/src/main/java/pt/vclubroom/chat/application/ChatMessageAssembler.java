package pt.vclubroom.chat.application;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;
import pt.vclubroom.chat.domain.ChatMessage;
import pt.vclubroom.identity.api.UserDirectory;
import pt.vclubroom.identity.api.UserSummary;
import pt.vclubroom.media.api.MediaCatalog;
import pt.vclubroom.media.api.MediaView;

/** Messages → views with one query for the authors and one for the media, whatever the page size. */
@Component
class ChatMessageAssembler {

    private final UserDirectory users;
    private final MediaCatalog mediaCatalog;

    ChatMessageAssembler(UserDirectory users, MediaCatalog mediaCatalog) {
        this.users = users;
        this.mediaCatalog = mediaCatalog;
    }

    List<ChatMessageView> toViews(List<ChatMessage> messages) {
        Map<UUID, UserSummary> authors = users.findByIds(messages.stream().map(ChatMessage::authorId).distinct().toList());
        Collection<UUID> mediaIds = messages.stream().filter(m -> !m.isDeleted()).flatMap(m -> m.mediaIds().stream()).toList();
        Map<UUID, MediaView> media = mediaCatalog.resolve(mediaIds);
        return messages.stream()
            .map(m -> new ChatMessageView(m.id(), m.channelId(), m.era(), authors.get(m.authorId()),
                m.isDeleted() ? null : m.body(),
                m.isDeleted() ? List.of() : m.mediaIds().stream().map(media::get).filter(Objects::nonNull).toList(),
                m.clientNonce(), m.isDeleted(), m.createdAt(), m.editedAt()))
            .toList();
    }

    ChatMessageView toView(ChatMessage message) {
        return toViews(List.of(message)).getFirst();
    }
}
