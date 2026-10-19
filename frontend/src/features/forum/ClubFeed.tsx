import { NewspaperIcon } from "lucide-react";
import { type ReactNode, useEffect, useRef } from "react";
import { Button } from "@/shared/ui/button";
import { EmptyState } from "@/shared/ui/EmptyState";
import { Skeleton } from "@/shared/ui/skeleton";
import { useChannelLive, useClubFeed } from "./api";
import { PostCard } from "./PostCard";

/** Listens to one channel of the feed (a component, so the feed can listen to as many channels as it shows). */
function ChannelLive({ channelId }: { channelId: string }) {
  useChannelLive(channelId);
  return null;
}

/**
 * The club feed (E09-S05): the latest posts of every forum channel of the club the viewer can read, newest first.
 * era = null: every era; a number: that era only (the History tab), read-only when it is not the current one.
 * channelLabel says where each post was posted (the caller knows the channels: forum does not import spaces).
 */
export function ClubFeed({ clubId, era, channelLabel, empty }: {
  clubId: string;
  era: number | null;
  channelLabel?: (channelId: string) => ReactNode;
  empty?: { title: string; description?: string };
}) {
  const { data, isPending, hasNextPage, fetchNextPage, isFetchingNextPage } = useClubFeed(clubId, era);
  const posts = data?.pages.flatMap((page) => page.items ?? []) ?? [];
  const channels = [...new Set(posts.map((post) => post.channelId).filter((id): id is string => !!id))];

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
      {channels.map((channelId) => <ChannelLive key={channelId} channelId={channelId} />)}
      {isPending ? (
        <div className="space-y-4" aria-hidden="true">{[0, 1].map((i) => <Skeleton key={i} className="h-40" />)}</div>
      ) : posts.length === 0 ? (
        <EmptyState icon={NewspaperIcon} title={empty?.title ?? "No posts yet"} description={empty?.description} />
      ) : (
        <ul className="space-y-4">
          {posts.map((post) => <li key={post.id}><PostCard post={post} context={channelLabel?.(post.channelId ?? "")} /></li>)}
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
