package pt.vclubroom.forum.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import pt.vclubroom.shared.error.BusinessRuleException;

/** A comment on a post, or a reply to one: one level only (D-26). E09-S04 gives soft deletion its endpoint. */
@Entity
@Table(name = "comments", schema = "forum")
public class Comment {

    @Id
    private UUID id;

    @Column(name = "post_id", nullable = false)
    private UUID postId;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(nullable = false)
    private String body;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "edited_at")
    private Instant editedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;

    protected Comment() {
        // JPA
    }

    /** BR-17: replies only to top-level comments of the same post. parent = null: a top-level comment. */
    public static Comment reply(Post post, Comment parent, UUID authorId, String body, Instant now) {
        if (parent != null && (!parent.postId.equals(post.id()) || parent.parentId != null)) {
            throw new BusinessRuleException("INVALID_PARENT", "You can only reply to a top-level comment of this post.");
        }
        Comment c = new Comment();
        c.id = UUID.randomUUID();
        c.postId = post.id();
        c.parentId = parent == null ? null : parent.id;
        c.authorId = authorId;
        c.body = body.strip();
        c.createdAt = now.truncatedTo(ChronoUnit.MICROS);
        return c;
    }

    public void edit(UUID editor, String newBody, Instant now) {
        requireNotDeleted();
        if (!authorId.equals(editor)) {
            throw new BusinessRuleException("NOT_AUTHOR", "Only the author can edit this comment.");
        }
        this.body = newBody.strip();
        this.editedAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    /** BR-16: tombstone, never a hard delete. Its replies stay. */
    public void softDelete(UUID by, Instant now) {
        requireNotDeleted();
        this.deletedBy = by;
        this.deletedAt = now.truncatedTo(ChronoUnit.MICROS);
    }

    private void requireNotDeleted() {
        if (isDeleted()) {
            throw new BusinessRuleException("COMMENT_DELETED", "This comment was deleted.");
        }
    }

    public String excerpt() {
        return body.length() <= 140 ? body : body.substring(0, 137) + "...";
    }

    public boolean isDeleted() { return deletedAt != null; }
    public boolean deletedByModerator() { return isDeleted() && !authorId.equals(deletedBy); }
    public UUID id() { return id; }
    public UUID postId() { return postId; }
    public UUID parentId() { return parentId; }
    public UUID authorId() { return authorId; }
    public String body() { return body; }
    public Instant createdAt() { return createdAt; }
    public Instant editedAt() { return editedAt; }
}
