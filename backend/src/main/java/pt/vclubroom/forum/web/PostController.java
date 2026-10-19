package pt.vclubroom.forum.web;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pt.vclubroom.forum.application.PostQueryService;
import pt.vclubroom.forum.application.PostService;
import pt.vclubroom.forum.web.dto.CreatePostRequest;
import pt.vclubroom.forum.web.dto.PostResponse;
import pt.vclubroom.shared.security.AuthenticatedUser;
import pt.vclubroom.shared.web.CursorPage;

@RestController
class PostController {

    private final PostService posts;
    private final PostQueryService queries;

    PostController(PostService posts, PostQueryService queries) {
        this.posts = posts;
        this.queries = queries;
    }

    @GetMapping("/api/channels/{channelId}/posts")
    CursorPage<PostResponse> list(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("channelId") UUID channelId,
                                  @RequestParam(name = "era", required = false) Integer era,
                                  @RequestParam(name = "cursor", required = false) String cursor,
                                  @RequestParam(name = "limit", defaultValue = "20") int limit) {
        return queries.list(me.actor(), channelId, era, cursor, Math.clamp(limit, 1, 50)).map(PostResponse::from);
    }

    @GetMapping("/api/posts/{postId}")
    PostResponse get(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("postId") UUID postId) {
        return PostResponse.from(queries.get(me.actor(), postId));
    }

    @PostMapping("/api/channels/{channelId}/posts")
    @ResponseStatus(HttpStatus.CREATED)
    PostResponse create(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable("channelId") UUID channelId,
                        @Valid @RequestBody CreatePostRequest request) {
        return PostResponse.from(posts.createPost(me.actor(), channelId, request.toCommand()));
    }
}
