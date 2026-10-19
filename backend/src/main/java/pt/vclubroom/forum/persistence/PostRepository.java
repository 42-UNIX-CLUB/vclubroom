package pt.vclubroom.forum.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pt.vclubroom.forum.domain.Post;

/**
 * Keyset pages, newest first: "older than (cursorAt, cursorId)" stays fast however deep you scroll, unlike OFFSET,
 * and a post published meanwhile never shifts the next page. E09-S05 adds the club feed, E09-S04 the pinned posts.
 */
public interface PostRepository extends JpaRepository<Post, UUID> {

    @Query("""
           select p from Post p
           where p.channelId = :channelId
             and (p.createdAt < :cursorAt or (p.createdAt = :cursorAt and p.id < :cursorId))
           order by p.createdAt desc, p.id desc
           """)
    List<Post> findPage(@Param("channelId") UUID channelId, @Param("cursorAt") Instant cursorAt,
                        @Param("cursorId") UUID cursorId, Limit limit);

    @Query("""
           select p from Post p
           where p.channelId = :channelId and p.era = :era
             and (p.createdAt < :cursorAt or (p.createdAt = :cursorAt and p.id < :cursorId))
           order by p.createdAt desc, p.id desc
           """)
    List<Post> findPageInEra(@Param("channelId") UUID channelId, @Param("era") int era,
                             @Param("cursorAt") Instant cursorAt, @Param("cursorId") UUID cursorId, Limit limit);
}
