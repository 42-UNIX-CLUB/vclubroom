import { type InfiniteData, type QueryClient, type QueryKey, useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/shared/api/client";
import type { components } from "@/shared/api/schema";
import { useTopic } from "@/shared/realtime/RealtimeProvider";

export type Post = components["schemas"]["PostResponse"];
export type NewPost = components["schemas"]["CreatePostRequest"];
export type Comment = components["schemas"]["CommentResponse"];
export type ReactionOption = components["schemas"]["ReactionOption"];
type ReactionsState = components["schemas"]["ReactionsResponse"];
type PostPage = components["schemas"]["CursorPagePostResponse"];

/** Query keys of this feature (08-frontend §5.1): ["channel", channelId, "posts", { era }], ["post", postId, "comments"]. */
export const forumKeys = {
  allPosts: (channelId: string) => ["channel", channelId, "posts"] as const,
  posts: (channelId: string, era: number | null) => ["channel", channelId, "posts", { era }] as const,
  comments: (postId: string) => ["post", postId, "comments"] as const,
  reactionOptions: ["reactions"] as const,
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
      updateCachedItems<PostPage>(queryClient, forumKeys.allPosts(channelId), postId,
        (post) => ({ ...post, commentCount: (post.commentCount ?? 0) + 1 }));
      return queryClient.invalidateQueries({ queryKey: forumKeys.comments(postId) });
    },
  });
}

/** Changes one post or comment wherever it sits in the cached pages under key (every era of a channel, for posts). */
function updateCachedItems<P extends { items?: { id?: string }[] }>(
  queryClient: QueryClient, key: QueryKey, id: string, change: (item: NonNullable<P["items"]>[number]) => NonNullable<P["items"]>[number],
) {
  queryClient.setQueriesData<InfiniteData<P>>({ queryKey: key }, (data) => data && {
    ...data,
    pages: data.pages.map((page) => ({ ...page, items: page.items?.map((item) => item.id === id ? change(item) : item) })),
  });
}

/** The fixed emoji set (GET /api/reactions): it never changes while the app runs. */
export function useReactionOptions() {
  return useQuery({
    queryKey: forumKeys.reactionOptions,
    queryFn: async () => (await api.GET("/api/reactions")).data ?? [],
    staleTime: Infinity,
  });
}

/** What a reaction bar works on: a post (cached in its channel's pages) or a comment (cached in its post's thread). */
export type ReactionTarget = { kind: "post"; id: string; channelId: string } | { kind: "comment"; id: string; postId: string };
type Reacted = { reactionCounts?: Record<string, number>; myReactions?: string[] };

/** The optimistic guess: one more (or one less) for this code, and it is mine (or not). */
function toggled<T extends Reacted>(item: T, code: string, add: boolean): T {
  const counts = { ...item.reactionCounts };
  const next = (counts[code] ?? 0) + (add ? 1 : -1);
  if (next > 0) counts[code] = next;
  else delete counts[code];
  const mine = new Set(item.myReactions);
  if (add) mine.add(code);
  else mine.delete(code);
  return { ...item, reactionCounts: counts, myReactions: [...mine] };
}

/**
 * Toggles a reaction optimistically (08-frontend §5.2): the count changes at once, rolls back if the server refuses,
 * and is replaced by the server's counts when it answers.
 */
export function useToggleReaction(target: ReactionTarget) {
  const queryClient = useQueryClient();
  const key = target.kind === "post" ? forumKeys.allPosts(target.channelId) : forumKeys.comments(target.postId);
  return useMutation({
    // Quick clicks on one target run one after the other, so the server sees them in the order they were made.
    scope: { id: `reaction:${target.kind}:${target.id}` },
    mutationFn: async ({ code, add }: { code: string; add: boolean }) => {
      if (target.kind === "post") {
        const params = { path: { postId: target.id, code } };
        return (add ? await api.PUT("/api/posts/{postId}/reactions/{code}", { params })
                    : await api.DELETE("/api/posts/{postId}/reactions/{code}", { params })).data as ReactionsState;
      }
      const params = { path: { commentId: target.id, code } };
      return (add ? await api.PUT("/api/comments/{commentId}/reactions/{code}", { params })
                  : await api.DELETE("/api/comments/{commentId}/reactions/{code}", { params })).data as ReactionsState;
    },
    onMutate: async ({ code, add }) => {
      await queryClient.cancelQueries({ queryKey: key });          // a refetch landing now would undo the guess
      const previous = queryClient.getQueriesData({ queryKey: key });
      updateCachedItems<{ items?: (Reacted & { id?: string })[] }>(queryClient, key, target.id, (item) => toggled(item, code, add));
      return { previous };
    },
    onError: (_error, _variables, context) => {
      context?.previous.forEach(([queryKey, data]) => queryClient.setQueryData(queryKey, data));
    },
    onSuccess: (state) => {
      updateCachedItems<{ items?: (Reacted & { id?: string })[] }>(queryClient, key, target.id,
        (item) => ({ ...item, reactionCounts: state.counts ?? {}, myReactions: state.mine ?? [] }));
    },
  });
}

type LivePayload = { postId?: string; commentId?: string; commentCount?: number; targetType?: string; targetId?: string; counts?: Record<string, number> };

/**
 * A feed listens to its channel (realtime, E10-S01): new posts reload the pages, comment counts and post reactions
 * are written into the cached posts. Pushes carry values, not changes, so my own actions are never counted twice.
 */
export function useChannelLive(channelId: string) {
  const queryClient = useQueryClient();
  useTopic<LivePayload>(channelId ? `/topic/channels/${channelId}/posts` : null, ({ type, payload }) => {
    const key = forumKeys.allPosts(channelId);
    if (type === "post.created") void queryClient.invalidateQueries({ queryKey: key });
    if (type === "comment.created" && payload.postId && payload.commentCount !== undefined) {
      updateCachedItems<PostPage>(queryClient, key, payload.postId, (post) => ({ ...post, commentCount: payload.commentCount }));
    }
    if (type === "reactions.changed" && payload.targetId) {
      updateCachedItems<PostPage>(queryClient, key, payload.targetId, (post) => ({ ...post, reactionCounts: payload.counts ?? {} }));
    }
  });
}

/** An open thread listens to its post: new comments reload it, comment reactions update their counts. */
export function usePostLive(postId: string) {
  const queryClient = useQueryClient();
  useTopic<LivePayload>(postId ? `/topic/posts/${postId}` : null, ({ type, payload }) => {
    const key = forumKeys.comments(postId);
    if (type === "comment.created") void queryClient.invalidateQueries({ queryKey: key });
    if (type === "reactions.changed" && payload.targetId) {
      updateCachedItems<{ items?: Comment[] }>(queryClient, key, payload.targetId, (comment) => ({ ...comment, reactionCounts: payload.counts ?? {} }));
    }
  });
}
