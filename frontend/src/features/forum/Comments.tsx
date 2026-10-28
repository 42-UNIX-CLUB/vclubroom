import { useState } from "react";
import { toast } from "sonner";
import { formatDateTime, formatRelative } from "@/shared/lib/dates";
import { Button } from "@/shared/ui/button";
import { ClubAvatar } from "@/shared/ui/ClubAvatar";
import { ConfirmDialog } from "@/shared/ui/ConfirmDialog";
import { Skeleton } from "@/shared/ui/skeleton";
import { Textarea } from "@/shared/ui/textarea";
import { type Comment, type Post, useAddComment, useComments, useDeleteComment, useEditComment, usePostLive } from "./api";
import { Reactions } from "./Reactions";

/**
 * The thread under a post (08-frontend §9.3): comments oldest first, each with its replies below it, one level
 * deep (BR-17). Writers get a comment box and a Reply button; readers and earlier eras only read. Authors edit and
 * delete their comments, moderators delete anyone's (canEdit, canDelete); a deleted one keeps its replies.
 */
export function Comments({ post }: { post: Post }) {
  const postId = post.id ?? "";
  const { data, isPending, hasNextPage, fetchNextPage, isFetchingNextPage } = useComments(postId);
  usePostLive(postId);                                      // other people's comments and reactions, live
  // One mutation for the thread: it stays mounted when the reply box closes, so the error toast still runs.
  const add = useAddComment(postId, post.channelId ?? "");
  const [replyTo, setReplyTo] = useState<string | null>(null);
  const comments = data?.pages.flatMap((page) => page.items ?? []) ?? [];
  const topLevel = comments.filter((comment) => !comment.parentId);
  const canWrite = !post.readOnly && !post.deleted;

  function send(body: string, parentId: string | undefined, clear: () => void) {
    add.mutate({ body, parentId }, {
      onSuccess: () => {
        clear();
        setReplyTo(null);
      },
      onError: (error) => toast.error(error.message),          // e.g. HISTORY_READ_ONLY
    });
  }

  return (
    <div className="space-y-3 border-t pt-3">
      {isPending ? (
        <Skeleton className="h-12" aria-hidden="true" />
      ) : topLevel.length === 0 ? (
        <p className="text-sm text-muted-foreground">No comments yet.</p>
      ) : (
        <ul aria-label="Comments" className="space-y-3">
          {topLevel.map((comment) => {
            const replies = comments.filter((reply) => reply.parentId === comment.id);
            return (
              <li key={comment.id} className="space-y-2">
                <CommentItem comment={comment} readOnly={!canWrite} />
                {replies.length > 0 && (
                  <ul aria-label={`Replies to ${comment.author?.displayName ?? "this comment"}`} className="ml-4 space-y-2 border-l pl-3">
                    {replies.map((reply) => <li key={reply.id}><CommentItem comment={reply} readOnly={!canWrite} /></li>)}
                  </ul>
                )}
                {canWrite && !comment.deleted && (replyTo === comment.id ? (
                  <div className="ml-4 pl-3">
                    <CommentBox label="Write a reply" busy={add.isPending} autoFocus onCancel={() => setReplyTo(null)}
                                onSend={(body, clear) => send(body, comment.id, clear)} />
                  </div>
                ) : (
                  <Button variant="link" size="sm" className="h-auto px-0" onClick={() => setReplyTo(comment.id ?? null)}>Reply</Button>
                ))}
              </li>
            );
          })}
        </ul>
      )}
      {hasNextPage && (
        <Button variant="ghost" size="sm" disabled={isFetchingNextPage} onClick={() => void fetchNextPage()}>
          {isFetchingNextPage ? "Loading…" : "Load more comments"}
        </Button>
      )}
      {canWrite && <CommentBox label="Write a comment" busy={add.isPending} onSend={(body, clear) => send(body, undefined, clear)} />}
    </div>
  );
}

function CommentItem({ comment, readOnly }: { comment: Comment; readOnly: boolean }) {
  const author = comment.author?.displayName ?? "Unknown user";
  const postId = comment.postId ?? "";
  const edit = useEditComment(postId);
  const remove = useDeleteComment(postId);
  const [editing, setEditing] = useState(false);
  const [deleting, setDeleting] = useState(false);
  return (
    <div className="flex gap-2 text-sm">
      <ClubAvatar name={author} url={comment.author?.avatarUrl} size="sm" />
      <div className="min-w-0 flex-1">
        <p>
          <span className="font-medium">{author}</span>{" "}
          {comment.createdAt && (
            <time dateTime={comment.createdAt} title={formatDateTime(comment.createdAt)} className="text-xs text-muted-foreground">
              {formatRelative(comment.createdAt)}{comment.editedAt && " · edited"}
            </time>
          )}
        </p>
        {comment.deleted ? (
          <p className="text-muted-foreground italic">{comment.deletedByModerator ? "Removed by a moderator." : "Deleted."}</p>
        ) : editing ? (
          <CommentBox label="Edit your comment" initial={comment.body ?? ""} submitLabel="Save" busy={edit.isPending} autoFocus
                      onCancel={() => setEditing(false)}
                      onSend={(body) => edit.mutate({ commentId: comment.id ?? "", body }, {
                        onSuccess: () => setEditing(false),
                        onError: (error) => toast.error(error.message),
                      })} />
        ) : (
          <>
            <p className="break-words whitespace-pre-wrap">{comment.body}</p>
            <div className="flex flex-wrap items-center gap-2">
              <Reactions target={{ kind: "comment", id: comment.id ?? "", postId }}
                         counts={comment.reactionCounts} mine={comment.myReactions} readOnly={readOnly} />
              {comment.canEdit && <Button variant="link" size="sm" className="h-auto px-0 text-muted-foreground" onClick={() => setEditing(true)}>Edit</Button>}
              {comment.canDelete && <Button variant="link" size="sm" className="h-auto px-0 text-muted-foreground" onClick={() => setDeleting(true)}>Delete</Button>}
            </div>
          </>
        )}
      </div>
      <ConfirmDialog open={deleting} onClose={() => setDeleting(false)} title="Delete this comment?" confirmLabel="Delete comment"
                     description="Its replies stay, under a note that it was deleted." onConfirm={() => remove.mutateAsync(comment.id ?? "")} />
    </div>
  );
}

function CommentBox({ label, busy, autoFocus, initial = "", submitLabel, onSend, onCancel }: {
  label: string;
  busy: boolean;
  autoFocus?: boolean;
  initial?: string;
  submitLabel?: string;
  onSend: (body: string, clear: () => void) => void;
  onCancel?: () => void;
}) {
  const [body, setBody] = useState(initial);
  return (
    <form
      className="flex flex-col gap-2 sm:flex-row sm:items-end"
      onSubmit={(event) => {
        event.preventDefault();
        onSend(body.trim(), () => setBody(""));
      }}
    >
      <Textarea aria-label={label} placeholder={`${label}…`} rows={1} className="min-h-9" autoFocus={autoFocus}
                value={body} onChange={(event) => setBody(event.target.value)} />
      <div className="flex gap-2">
        {onCancel && <Button type="button" variant="ghost" size="sm" onClick={onCancel}>Cancel</Button>}
        <Button type="submit" size="sm" disabled={!body.trim() || busy}>{submitLabel ?? (onCancel ? "Send reply" : "Comment")}</Button>
      </div>
    </form>
  );
}
