package pt.vclubroom.chat.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pt.vclubroom.chat.domain.ChatMessage;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    Optional<ChatMessage> findByChannelIdAndAuthorIdAndClientNonce(UUID channelId, UUID authorId, UUID clientNonce);

    /** Newest first, older than before (the first page: Long.MAX_VALUE). */
    @Query("select m from ChatMessage m where m.channelId = :channelId and m.id < :before order by m.id desc")
    List<ChatMessage> findPage(@Param("channelId") UUID channelId, @Param("before") long before, Limit limit);

    @Query("""
           select m from ChatMessage m
           where m.channelId = :channelId and m.era = :era and m.id < :before
           order by m.id desc
           """)
    List<ChatMessage> findPageInEra(@Param("channelId") UUID channelId, @Param("era") int era,
                                    @Param("before") long before, Limit limit);
}
