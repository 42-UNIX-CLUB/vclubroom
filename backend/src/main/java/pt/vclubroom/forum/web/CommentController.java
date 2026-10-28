package pt.vclubroom.forum.web;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pt.vclubroom.forum.application.CommentService;
import pt.vclubroom.forum.web.dto.CommentResponse;
import pt.vclubroom.forum.web.dto.CreateCommentRequest;
import pt.vclubroom.forum.web.dto.UpdateCommentRequest;
import pt.vclubroom.shared.security.AuthenticatedUser;
import pt.vclubroom.shared.web.CursorPage;

@RestController
class CommentController {

    private final CommentService comments;

    CommentController(CommentService comments) {
        this.comments = comments;
    }

    @GetMapping("/api/posts/{postId}/comments")
    CursorPage<CommentResponse> list(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("postId") UUID postId,
                                     @RequestParam(name = "cursor", required = false) String cursor,
                                     @RequestParam(name = "limit", defaultValue = "50") int limit) {
        return comments.list(me.actor(), postId, cursor, Math.clamp(limit, 1, 100)).map(CommentResponse::from);
    }

    @PostMapping("/api/posts/{postId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    CommentResponse create(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("postId") UUID postId,
                           @Valid @RequestBody CreateCommentRequest request) {
        return CommentResponse.from(comments.addComment(me.actor(), postId, request.body(), request.parentId()));
    }

    @PatchMapping("/api/comments/{commentId}")
    CommentResponse edit(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("commentId") UUID commentId,
                         @Valid @RequestBody UpdateCommentRequest request) {
        return CommentResponse.from(comments.edit(me.actor(), commentId, request.body()));
    }

    @DeleteMapping("/api/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("commentId") UUID commentId) {
        comments.delete(me.actor(), commentId);
    }
}
