import { HistoryIcon, NewspaperIcon } from "lucide-react";
import { useEffect, useRef } from "react";
import { Link, useSearchParams } from "react-router";
import { Button, buttonVariants } from "@/shared/ui/button";
import { EmptyState } from "@/shared/ui/EmptyState";
import { Skeleton } from "@/shared/ui/skeleton";
import { useChannelLive, useChannelPosts, usePinnedPosts } from "./api";
import { PostCard } from "./PostCard";
import { PostComposer } from "./PostComposer";

/** What the forum needs from the channel the space module found (ChannelView). */
export interface FeedChannel {
  id?: string;
  canWrite?: boolean;
  currentEra?: number;
}

/**
 * A forum channel: the composer (if you may write), the pinned posts (E09-S04), then the others, newest first,
 * loading older ones as you scroll. ?era=n shows an earlier era of the club, read-only (BR-15), without pins.
 */
export function ChannelFeed({ channel }: { channel: FeedChannel }) {
  const [params] = useSearchParams();
  const era = params.get("era") ? Number(params.get("era")) : null;
  const viewingHistory = era !== null && era !== channel.currentEra;
  const { data, isPending, hasNextPage, fetchNextPage, isFetchingNextPage } = useChannelPosts(channel.id ?? "", era);
  useChannelLive(channel.id ?? "");                         // new posts, counts and reactions as they happen
  const { data: pinned = [] } = usePinnedPosts(viewingHistory ? "" : channel.id ?? "");
  const shownPinned = viewingHistory ? [] : pinned;
  const pinnedIds = new Set(shownPinned.map((post) => post.id));
  const posts = (data?.pages.flatMap((page) => page.items ?? []) ?? []).filter((post) => !pinnedIds.has(post.id));

  // Infinite scroll: load the next page when the end of the list comes into view (the button works without it).
  const end = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!hasNextPage || !end.current || typeof IntersectionObserver === "undefined") return;
    const observer = new IntersectionObserver((entries) => {
      if (entries[0]?.isIntersecting && !isFetchingNextPage) void fetchNextPage();
    });
    observer.observe(end.current);
    return () => observer.disconnect();
  }, [hasNextPage, isFetchingNextPage, fetchNextPage]);

  return (
    <div className="space-y-4">
      {viewingHistory && (
        <div role="status" className="flex flex-wrap items-center justify-between gap-2 rounded-xl border bg-muted/50 p-3 text-sm">
          <span className="flex items-center gap-2"><HistoryIcon className="size-4" aria-hidden="true" /> Era {era}: read-only history.</span>
          <Link to="?" className={buttonVariants({ variant: "outline", size: "sm" })}>Back to era {channel.currentEra}</Link>
        </div>
      )}
      {channel.canWrite && !viewingHistory && channel.id && <PostComposer channelId={channel.id} />}

      {shownPinned.length > 0 && (
        <section aria-labelledby="pinned-heading" className="space-y-3">
          <h2 id="pinned-heading" className="text-sm font-semibold text-muted-foreground">Pinned</h2>
          <ul className="space-y-4">
            {shownPinned.map((post) => <li key={post.id}><PostCard post={post} /></li>)}
          </ul>
        </section>
      )}

      {isPending ? (
        <div className="space-y-4" aria-hidden="true">{[0, 1].map((i) => <Skeleton key={i} className="h-40" />)}</div>
      ) : posts.length === 0 && shownPinned.length === 0 ? (
        <EmptyState
          icon={NewspaperIcon}
          title="No posts yet"
          description={channel.canWrite && !viewingHistory ? "Be the first to share something." : undefined}
        />
      ) : (
        <ul className="space-y-4">
          {posts.map((post) => <li key={post.id}><PostCard post={post} /></li>)}
        </ul>
      )}

      <div ref={end} />
      {hasNextPage && (
        <div className="flex justify-center">
          <Button variant="outline" disabled={isFetchingNextPage} onClick={() => void fetchNextPage()}>
            {isFetchingNextPage ? "Loading…" : "Load older posts"}
          </Button>
        </div>
      )}
    </div>
  );
}
