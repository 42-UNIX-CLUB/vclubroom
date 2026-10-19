package pt.vclubroom.forum.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import pt.vclubroom.shared.error.BusinessRuleException;
import pt.vclubroom.shared.error.InvalidRequestException;

/**
 * A post in a forum channel; a post is a thread (D-05). channelId, spaceId and clubId are copied in at creation and
 * never change. era stamps the club era it belongs to: posts of earlier eras are read-only (BR-15).
 */
@Entity
@Table(name = "posts", schema = "forum")
public class Post {

    @Id
    private UUID id;

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

    private String title;

    @Column(nullable = false)
    private String body;

    @ElementCollection
    @CollectionTable(name = "post_media", schema = "forum", joinColumns = @JoinColumn(name = "post_id"))
    @OrderColumn(name = "position")
    @Column(name = "media_id", nullable = false)
    private List<UUID> mediaIds = new ArrayList<>();

    @Column(nullable = false)
    private boolean pinned;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PostStatus status;

    @Column(name = "comment_count", nullable = false)
    private int commentCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    @Column(name = "edited_at")
    private Instant editedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    @Version
    private Long version;

    protected Post() {
        // JPA
    }

    public static Post publish(UUID channelId, UUID spaceId, UUID clubId, int era, UUID authorId,
                               String title, String body, List<UUID> mediaIds, Instant now) {
        String text = body == null ? "" : body.strip();
        if (text.isEmpty() && (mediaIds == null || mediaIds.isEmpty())) {
            throw new InvalidRequestException("EMPTY_POST", "A post needs text or at least one attachment.");
        }
        Post p = new Post();
        p.id = UUID.randomUUID();
        p.channelId = channelId;
        p.spaceId = spaceId;
        p.clubId = clubId;
        p.era = era;
        p.authorId = authorId;
        p.title = title == null || title.isBlank() ? null : title.strip();
        p.body = text;
        p.mediaIds = new ArrayList<>(mediaIds == null ? List.of() : mediaIds);
        p.status = PostStatus.PUBLISHED;
        p.createdAt = now.truncatedTo(ChronoUnit.MICROS);
        p.lastActivityAt = p.createdAt;
        return p;
    }

    public void edit(UUID editor, String newTitle, String newBody, Instant now) {
        requirePublished();
        if (!authorId.equals(editor)) {
            throw new BusinessRuleException("NOT_AUTHOR", "Only the author can edit this post.");
        }
        this.title = newTitle;
        this.body = newBody.strip();
        this.editedAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    /** BR-16: tombstone, never a hard delete. */
    public void softDelete(UUID by, Instant now) {
        requirePublished();
        this.status = PostStatus.DELETED;
        this.deletedBy = by;
        this.deletedAt = now.truncatedTo(ChronoUnit.MICROS);
        this.pinned = false;
    }

    public void pin(boolean value) {
        requirePublished();
        this.pinned = value;
    }

    public void commentAdded(Instant now) {
        this.commentCount++;
        this.lastActivityAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    public String excerpt() {
        return body.length() <= 140 ? body : body.substring(0, 137) + "...";
    }

    private void requirePublished() {
        if (status != PostStatus.PUBLISHED) {
            throw new BusinessRuleException("POST_DELETED", "This post was deleted.");
        }
    }

    public boolean isDeleted() { return status == PostStatus.DELETED; }
    public boolean deletedByModerator() { return isDeleted() && !authorId.equals(deletedBy); }
    public UUID id() { return id; }
    public UUID channelId() { return channelId; }
    public UUID spaceId() { return spaceId; }
    public UUID clubId() { return clubId; }
    public UUID authorId() { return authorId; }
    public int era() { return era; }
    public String title() { return title; }
    public String body() { return body; }
    public List<UUID> mediaIds() { return Collections.unmodifiableList(mediaIds); }
    public boolean pinned() { return pinned; }
    public int commentCount() { return commentCount; }
    public Instant createdAt() { return createdAt; }
    public Instant editedAt() { return editedAt; }
}
