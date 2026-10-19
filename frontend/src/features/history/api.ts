import { useInfiniteQuery } from "@tanstack/react-query";
import { api } from "@/shared/api/client";
import type { components } from "@/shared/api/schema";

export type TimelineEntry = components["schemas"]["TimelineEntryView"];

/** Query keys of this feature (08-frontend §5.1): under ["club", clubId], so club changes refresh the timeline. */
export const historyKeys = {
  timeline: (clubId: string) => ["club", clubId, "timeline"] as const,
};

/** The club's lifecycle facts, newest first, 30 per page. */
export function useTimeline(clubId: string) {
  return useInfiniteQuery({
    queryKey: historyKeys.timeline(clubId),
    queryFn: async ({ pageParam }) => (await api.GET("/api/clubs/{clubId}/timeline", {
      params: { path: { clubId }, query: { cursor: pageParam, limit: 30 } },
    })).data ?? { items: [] },
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
    enabled: !!clubId,
  });
}
