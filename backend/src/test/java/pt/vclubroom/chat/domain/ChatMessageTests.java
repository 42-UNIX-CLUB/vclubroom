package pt.vclubroom.chat.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pt.vclubroom.shared.error.BusinessRuleException;
import pt.vclubroom.shared.error.InvalidRequestException;

class ChatMessageTests {

    private static final Instant NOW = Instant.parse("2026-10-19T10:00:00Z");
    private final UUID author = UUID.randomUUID();

    private ChatMessage message(String body) {
        return ChatMessage.post(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, author, body, List.of(), UUID.randomUUID(), NOW);
    }

    @Test
    void aMessageNeedsTextOrAnAttachment() {
        assertThatThrownBy(() -> message("  "))
            .isInstanceOfSatisfying(InvalidRequestException.class, e -> assertThat(e.code()).isEqualTo("EMPTY_MESSAGE"));
        assertThat(message("  gg  ").body()).isEqualTo("gg");
    }

    @Test
    void onlyItsAuthorEditsItAndDeletingTwiceIsHarmless() {
        ChatMessage m = message("gg");
        assertThatThrownBy(() -> m.edit(UUID.randomUUID(), "mine now", NOW))
            .isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.code()).isEqualTo("CANNOT_EDIT"));
        m.edit(author, "good game", NOW);
        assertThat(m.body()).isEqualTo("good game");
        m.softDelete(author, NOW);
        m.softDelete(UUID.randomUUID(), NOW.plusSeconds(60));
        assertThat(m.isDeleted()).isTrue();
        assertThatThrownBy(() -> m.edit(author, "back", NOW)).isInstanceOf(BusinessRuleException.class);
    }
}
