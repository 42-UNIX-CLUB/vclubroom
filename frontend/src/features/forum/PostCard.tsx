import { MessageSquareIcon } from "lucide-react";
import { type ReactNode, useState } from "react";
import { MediaView } from "@/features/media";
import { formatDateTime, formatRelative } from "@/shared/lib/dates";
import { Button } from "@/shared/ui/button";
import { Card, CardContent, CardHeader } from "@/shared/ui/card";
import { ClubAvatar } from "@/shared/ui/ClubAvatar";
import { Markdown } from "@/shared/ui/Markdown";
import type { Post } from "./api";
import { Comments } from "./Comments";
import { Reactions } from "./Reactions";

/**
 * One post: author and time, optional title, Markdown body, players for its media, and its thread on demand.
 * context: where it was posted, for lists that mix channels (the club feed).
 */
export function PostCard({ post, context }: { post: Post; context?: ReactNode }) {
  const author = post.author?.displayName ?? "Unknown user";
  const [showComments, setShowComments] = useState(false);       // closed by default: no request per post on a page
  const count = post.commentCount ?? 0;
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
          {context && <span className="text-muted-foreground"> · {context}</span>}
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
        <div className="flex flex-wrap items-center gap-2">
          {!post.deleted && (
            <Reactions target={{ kind: "post", id: post.id ?? "", channelId: post.channelId ?? "" }}
              counts={post.reactionCounts} mine={post.myReactions} readOnly={post.readOnly} />
          )}
          <Button variant="ghost" size="sm" className="text-muted-foreground" aria-expanded={showComments}
            onClick={() => setShowComments((open) => !open)}>
            <MessageSquareIcon aria-hidden="true" />
            {count} {count === 1 ? "comment" : "comments"}
          </Button>
        </div>
        {showComments && <Comments post={post} />}
      </CardContent>
    </Card>
  );
}
