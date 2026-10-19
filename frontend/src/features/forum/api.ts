import { useInfiniteQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/shared/api/client";
import type { components } from "@/shared/api/schema";

export type Post = components["schemas"]["PostResponse"];
export type NewPost = components["schemas"]["CreatePostRequest"];

/** Query keys of this feature (08-frontend §5.1): ["channel", channelId, "posts", { era }]. */
export const forumKeys = {
  allPosts: (channelId: string) => ["channel", channelId, "posts"] as const,
  posts: (channelId: string, era: number | null) => ["channel", channelId, "posts", { era }] as const,
};

/** Newest first, 20 per page; each page carries the cursor of the next (older) one. era = null: every era. */
export function useChannelPosts(channelId: string, era: number | null) {
  return useInfiniteQuery({
    queryKey: forumKeys.posts(channelId, era),
    queryFn: async ({ pageParam }) => (await api.GET("/api/channels/{channelId}/posts", {
      params: { path: { channelId }, query: { era: era ?? undefined, cursor: pageParam, limit: 20 } },
    })).data ?? { items: [] },
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
  });
}

export function useCreatePost(channelId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (body: NewPost) =>
      (await api.POST("/api/channels/{channelId}/posts", { params: { path: { channelId } }, body })).data as Post,
    // The new post must appear on top: reload the channel's pages (E10-S01 adds the real-time push for others).
    onSuccess: () => queryClient.invalidateQueries({ queryKey: forumKeys.allPosts(channelId) }),
  });
}
