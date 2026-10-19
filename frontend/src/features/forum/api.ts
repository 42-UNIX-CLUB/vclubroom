import { type InfiniteData, useInfiniteQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/shared/api/client";
import type { components } from "@/shared/api/schema";

export type Post = components["schemas"]["PostResponse"];
export type NewPost = components["schemas"]["CreatePostRequest"];
export type Comment = components["schemas"]["CommentResponse"];
type PostPage = components["schemas"]["CursorPagePostResponse"];

/** Query keys of this feature (08-frontend §5.1): ["channel", channelId, "posts", { era }], ["post", postId, "comments"]. */
export const forumKeys = {
  allPosts: (channelId: string) => ["channel", channelId, "posts"] as const,
  posts: (channelId: string, era: number | null) => ["channel", channelId, "posts", { era }] as const,
  comments: (postId: string) => ["post", postId, "comments"] as const,
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

/** A post's thread, oldest first, 50 per page; replies come mixed in by time and carry their parentId. */
export function useComments(postId: string) {
  return useInfiniteQuery({
    queryKey: forumKeys.comments(postId),
    queryFn: async ({ pageParam }) => (await api.GET("/api/posts/{postId}/comments", {
      params: { path: { postId }, query: { cursor: pageParam, limit: 50 } },
    })).data ?? { items: [] },
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
  });
}

export function useAddComment(postId: string, channelId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (body: { body: string; parentId?: string }) =>
      (await api.POST("/api/posts/{postId}/comments", { params: { path: { postId } }, body })).data as Comment,
    onSuccess: () => {
      // The post's counter goes up in the cached channel pages: cheaper than reloading every page of posts.
      queryClient.setQueriesData<InfiniteData<PostPage>>({ queryKey: forumKeys.allPosts(channelId) }, (data) => data && {
        ...data,
        pages: data.pages.map((page) => ({
          ...page,
          items: page.items?.map((post) => post.id === postId ? { ...post, commentCount: (post.commentCount ?? 0) + 1 } : post),
        })),
      });
      return queryClient.invalidateQueries({ queryKey: forumKeys.comments(postId) });
    },
  });
}
