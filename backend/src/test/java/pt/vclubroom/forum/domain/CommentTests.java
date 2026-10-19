package pt.vclubroom.forum.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import pt.vclubroom.shared.error.BusinessRuleException;

class CommentTests {

    private static final Instant NOW = Instant.parse("2026-10-19T10:00:00Z");

    private static Post post() {
        return Post.publish(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, UUID.randomUUID(), null, "Hi", List.of(), NOW);
    }

    @Test
    void aReplyHangsUnderATopLevelComment() {
        Post post = post();
        Comment top = Comment.reply(post, null, UUID.randomUUID(), "  Nice!  ", NOW);
        Comment reply = Comment.reply(post, top, UUID.randomUUID(), "Agreed", NOW);

        assertThat(top.parentId()).isNull();
        assertThat(top.body()).isEqualTo("Nice!");
        assertThat(reply.parentId()).isEqualTo(top.id());
    }

    @Test
    void repliesGoOneLevelDeepAndStayInTheirPost() {
        Post post = post();
        Comment top = Comment.reply(post, null, UUID.randomUUID(), "Nice!", NOW);
        Comment reply = Comment.reply(post, top, UUID.randomUUID(), "Agreed", NOW);

        assertThatThrownBy(() -> Comment.reply(post, reply, UUID.randomUUID(), "Deeper", NOW))         // BR-17
            .isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.code()).isEqualTo("INVALID_PARENT"));
        assertThatThrownBy(() -> Comment.reply(post(), top, UUID.randomUUID(), "Elsewhere", NOW))
            .isInstanceOfSatisfying(BusinessRuleException.class, e -> assertThat(e.code()).isEqualTo("INVALID_PARENT"));
    }
}
