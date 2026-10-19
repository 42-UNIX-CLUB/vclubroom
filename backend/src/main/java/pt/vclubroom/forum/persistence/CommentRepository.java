package pt.vclubroom.forum.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pt.vclubroom.forum.domain.Comment;

public interface CommentRepository extends JpaRepository<Comment, UUID> {

    /**
     * Keyset page, oldest first, replies mixed in by time. A reply is always younger than its parent, so the parent
     * comes on the same page or an earlier one, and the SPA can hang each reply under it.
     */
    @Query("""
           select c from Comment c
           where c.postId = :postId
             and (c.createdAt > :cursorAt or (c.createdAt = :cursorAt and c.id > :cursorId))
           order by c.createdAt asc, c.id asc
           """)
    List<Comment> findPage(@Param("postId") UUID postId, @Param("cursorAt") Instant cursorAt,
                           @Param("cursorId") UUID cursorId, Limit limit);
}
