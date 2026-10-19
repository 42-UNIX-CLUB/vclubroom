import { MessageSquareIcon } from "lucide-react";
import { MediaView } from "@/features/media";
import { formatDateTime, formatRelative } from "@/shared/lib/dates";
import { Card, CardContent, CardHeader } from "@/shared/ui/card";
import { ClubAvatar } from "@/shared/ui/ClubAvatar";
import { Markdown } from "@/shared/ui/Markdown";
import type { Post } from "./api";

/** One post: author and time, optional title, Markdown body, players for its media. */
export function PostCard({ post }: { post: Post }) {
  const author = post.author?.displayName ?? "Unknown user";
  return (
    <Card>
      <CardHeader className="flex flex-row items-center gap-3">
        <ClubAvatar name={author} url={post.author?.avatarUrl} />
        <div className="min-w-0 text-sm">
          <p className="truncate font-medium">{author}</p>
          {post.createdAt && (
            <time dateTime={post.createdAt} title={formatDateTime(post.createdAt)} className="text-muted-foreground">
              {formatRelative(post.createdAt)}{post.editedAt && " · edited"}
            </time>
          )}
        </div>
      </CardHeader>
      <CardContent className="space-y-3">
        {post.deleted ? (
          <p className="text-sm text-muted-foreground italic">
            {post.deletedByModerator ? "This post was removed by a moderator." : "This post was deleted."}
          </p>
        ) : (
          <>
            {post.title && <h3 className="text-lg font-semibold">{post.title}</h3>}
            {post.body && <Markdown>{post.body}</Markdown>}
            {!!post.media?.length && (
              <div className="grid gap-2 sm:grid-cols-2">
                {post.media.map((media) => <MediaView key={media.id} media={media} />)}
              </div>
            )}
          </>
        )}
        {/* E09-S02 turns this into the comments thread. */}
        <p className="flex items-center gap-1.5 text-xs text-muted-foreground">
          <MessageSquareIcon className="size-3.5" aria-hidden="true" />
          {post.commentCount ?? 0} {post.commentCount === 1 ? "comment" : "comments"}
        </p>
      </CardContent>
    </Card>
  );
}
