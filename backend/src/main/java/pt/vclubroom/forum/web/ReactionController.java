package pt.vclubroom.forum.web;

import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import pt.vclubroom.forum.application.ForumProperties;
import pt.vclubroom.forum.application.ReactionService;
import pt.vclubroom.forum.domain.ReactionTarget;
import pt.vclubroom.forum.web.dto.ReactionOption;
import pt.vclubroom.forum.web.dto.ReactionsResponse;
import pt.vclubroom.shared.security.AuthenticatedUser;

/** PUT adds, DELETE removes; both idempotent and answer the target's reactions afterwards. */
@RestController
class ReactionController {

    private final ReactionService reactions;
    private final ForumProperties properties;

    ReactionController(ReactionService reactions, ForumProperties properties) {
        this.reactions = reactions;
        this.properties = properties;
    }

    @GetMapping("/api/reactions")
    List<ReactionOption> options() {
        return properties.reactions().entrySet().stream().map(e -> new ReactionOption(e.getKey(), e.getValue())).toList();
    }

    @PutMapping("/api/posts/{postId}/reactions/{code}")
    ReactionsResponse reactToPost(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("postId") UUID postId,
                                  @PathVariable("code") String code) {
        return ReactionsResponse.from(reactions.react(me.actor(), ReactionTarget.POST, postId, code, true));
    }

    @DeleteMapping("/api/posts/{postId}/reactions/{code}")
    ReactionsResponse unreactToPost(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("postId") UUID postId,
                                    @PathVariable("code") String code) {
        return ReactionsResponse.from(reactions.react(me.actor(), ReactionTarget.POST, postId, code, false));
    }

    @PutMapping("/api/comments/{commentId}/reactions/{code}")
    ReactionsResponse reactToComment(@AuthenticationPrincipal AuthenticatedUser me,
                                     @PathVariable("commentId") UUID commentId, @PathVariable("code") String code) {
        return ReactionsResponse.from(reactions.react(me.actor(), ReactionTarget.COMMENT, commentId, code, true));
    }

    @DeleteMapping("/api/comments/{commentId}/reactions/{code}")
    ReactionsResponse unreactToComment(@AuthenticationPrincipal AuthenticatedUser me,
                                       @PathVariable("commentId") UUID commentId, @PathVariable("code") String code) {
        return ReactionsResponse.from(reactions.react(me.actor(), ReactionTarget.COMMENT, commentId, code, false));
    }
}
