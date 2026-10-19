package pt.vclubroom.forum.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pt.vclubroom.shared.error.InvalidRequestException;

class PostTests {

    private static final Instant NOW = Instant.parse("2026-10-19T09:00:00Z");
    private final UUID channel = UUID.randomUUID();

    private Post post(String body, List<UUID> media) {
        return Post.publish(channel, UUID.randomUUID(), UUID.randomUUID(), 1, UUID.randomUUID(), "  ", body, media, NOW);
    }

    @Test
    void aPostNeedsTextOrMedia() {
        assertThatThrownBy(() -> post("   ", List.of()))
            .isInstanceOfSatisfying(InvalidRequestException.class, e -> assertThat(e.code()).isEqualTo("EMPTY_POST"));
        assertThat(post("", List.of(UUID.randomUUID())).body()).isEmpty();          // media only is fine
        assertThat(post("  Hello  ", null).body()).isEqualTo("Hello");
    }

    @Test
    void aBlankTitleIsNoTitle() {
        assertThat(post("Hello", List.of()).title()).isNull();
    }

    @Test
    void theExcerptIsShortForNotifications() {
        assertThat(post("x".repeat(200), List.of()).excerpt()).hasSize(140).endsWith("...");
        assertThat(post("short", List.of()).excerpt()).isEqualTo("short");
    }
}
