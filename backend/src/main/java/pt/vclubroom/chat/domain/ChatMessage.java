package pt.vclubroom.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import pt.vclubroom.shared.error.BusinessRuleException;
import pt.vclubroom.shared.error.InvalidRequestException;

/**
 * One chat message. The id is a database sequence: monotonic, so "older than id" is the whole keyset. The browser's
 * clientNonce is unique per (channel, author), so a retried send finds the first one instead of adding a second.
 */
@Entity
@Table(name = "messages", schema = "chat")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "channel_id", nullable = false)
    private UUID channelId;

    @Column(name = "space_id", nullable = false)
    private UUID spaceId;

    @Column(name = "club_id", nullable = false)
    private UUID clubId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(nullable = false)
    private int era;

    @Column(nullable = false)
    private String body;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "media_ids", nullable = false, columnDefinition = "uuid[]")
    private List<UUID> mediaIds = new ArrayList<>();

    @Column(name = "client_nonce", nullable = false)
    private UUID clientNonce;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "edited_at")
    private Instant editedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    protected ChatMessage() {
        // JPA
    }

    public static ChatMessage post(UUID channelId, UUID spaceId, UUID clubId, int era, UUID authorId,
                                   String body, List<UUID> mediaIds, UUID clientNonce, Instant now) {
        String text = body == null ? "" : body.strip();
        if (text.isEmpty() && (mediaIds == null || mediaIds.isEmpty())) {
            throw new InvalidRequestException("EMPTY_MESSAGE", "A message needs text or an attachment.");
        }
        ChatMessage m = new ChatMessage();
        m.channelId = channelId;
        m.spaceId = spaceId;
        m.clubId = clubId;
        m.era = era;
        m.authorId = authorId;
        m.body = text;
        m.mediaIds = new ArrayList<>(mediaIds == null ? List.of() : mediaIds);
        m.clientNonce = clientNonce;
        m.createdAt = now.truncatedTo(ChronoUnit.MICROS);
        return m;
    }

    public void edit(UUID editor, String newBody, Instant now) {
        if (!authorId.equals(editor) || deletedAt != null) {
            throw new BusinessRuleException("CANNOT_EDIT", "You cannot edit this message.");
        }
        String text = newBody == null ? "" : newBody.strip();
        if (text.isEmpty()) {
            throw new InvalidRequestException("EMPTY_MESSAGE", "A message needs text.");
        }
        this.body = text;
        this.editedAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    /** BR-16: the row stays; the body is no longer shown. */
    public void softDelete(UUID by, Instant now) {
        if (deletedAt != null) {
            return;                                        // deleting twice changes nothing
        }
        this.deletedBy = by;
        this.deletedAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    public boolean isDeleted() { return deletedAt != null; }
    public Long id() { return id; }
    public UUID channelId() { return channelId; }
    public UUID authorId() { return authorId; }
    public int era() { return era; }
    public String body() { return body; }
    public List<UUID> mediaIds() { return List.copyOf(mediaIds); }
    public UUID clientNonce() { return clientNonce; }
    public Instant createdAt() { return createdAt; }
    public Instant editedAt() { return editedAt; }
}
