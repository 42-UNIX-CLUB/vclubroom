import { EllipsisIcon, PencilIcon, PinIcon, PinOffIcon, Trash2Icon } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import { Button } from "@/shared/ui/button";
import { ConfirmDialog } from "@/shared/ui/ConfirmDialog";
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from "@/shared/ui/dialog";
import {
  DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger,
} from "@/shared/ui/dropdown-menu";
import { Field, FieldLabel } from "@/shared/ui/field";
import { Input } from "@/shared/ui/input";
import { Textarea } from "@/shared/ui/textarea";
import { type Post, useDeletePost, useEditPost, usePinPost } from "./api";

/**
 * What the viewer may do with a post (E09-S04): the server says which actions apply (canEdit, canPin, canDelete),
 * so the menu shows those only, and nothing at all when there are none.
 */
export function PostActions({ post }: { post: Post }) {
  const channelId = post.channelId ?? "";
  const postId = post.id ?? "";
  const pin = usePinPost(channelId);
  const remove = useDeletePost(channelId);
  const [editing, setEditing] = useState(false);
  const [deleting, setDeleting] = useState(false);
  if (!post.canEdit && !post.canPin && !post.canDelete) return null;
  const mine = post.canEdit;

  return (
    <>
      <DropdownMenu>
        <DropdownMenuTrigger render={<Button variant="ghost" size="icon-sm" aria-label="Post actions" />}>
          <EllipsisIcon />
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          {post.canEdit && <DropdownMenuItem onClick={() => setEditing(true)}><PencilIcon /> Edit</DropdownMenuItem>}
          {post.canPin && (
            <DropdownMenuItem onClick={() => pin.mutate({ postId, pinned: !post.pinned }, { onError: (error) => toast.error(error.message) })}>
              {post.pinned ? <><PinOffIcon /> Unpin</> : <><PinIcon /> Pin</>}
            </DropdownMenuItem>
          )}
          {post.canDelete && (
            <DropdownMenuItem variant="destructive" onClick={() => setDeleting(true)}><Trash2Icon /> Delete</DropdownMenuItem>
          )}
        </DropdownMenuContent>
      </DropdownMenu>
      {editing && <EditPostDialog post={post} onClose={() => setEditing(false)} />}
      <ConfirmDialog open={deleting} onClose={() => setDeleting(false)} confirmLabel="Delete post"
                     title="Delete this post?"
                     description={mine ? "It is replaced by “This post was deleted.” Its comments stay."
                       : "It is replaced by “This post was removed by a moderator.” Its comments stay."}
                     onConfirm={() => remove.mutateAsync(postId)} />
    </>
  );
}

/** The title and text; attachments stay as they are. */
function EditPostDialog({ post, onClose }: { post: Post; onClose: () => void }) {
  const edit = useEditPost(post.channelId ?? "");
  const [title, setTitle] = useState(post.title ?? "");
  const [body, setBody] = useState(post.body ?? "");
  const empty = !body.trim() && !post.media?.length;

  function onSave() {
    edit.mutate({ postId: post.id ?? "", title: title.trim() || undefined, body: body.trim() }, {
      onSuccess: onClose,
      onError: (error) => toast.error(error.message),
    });
  }

  return (
    <Dialog open onOpenChange={(open) => { if (!open) onClose(); }}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Edit post</DialogTitle>
          <DialogDescription>Readers see that it was edited.</DialogDescription>
        </DialogHeader>
        <Field>
          <FieldLabel htmlFor="post-title">Title (optional)</FieldLabel>
          <Input id="post-title" maxLength={140} value={title} onChange={(e) => setTitle(e.target.value)} />
        </Field>
        <Field>
          <FieldLabel htmlFor="post-body">Text</FieldLabel>
          <Textarea id="post-body" rows={6} maxLength={20000} value={body} onChange={(e) => setBody(e.target.value)} />
        </Field>
        <DialogFooter>
          <Button variant="ghost" onClick={onClose}>Cancel</Button>
          <Button disabled={empty || edit.isPending} onClick={onSave}>Save</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
