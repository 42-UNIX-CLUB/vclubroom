import { type InfiniteData, type QueryClient, useInfiniteQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { api } from "@/shared/api/client";
import type { components } from "@/shared/api/schema";
import { useTopic } from "@/shared/realtime/RealtimeProvider";

export type ChatMessage = components["schemas"]["ChatMessageView"];
type ChatPage = components["schemas"]["ChatPage"];

/** Query keys of this feature (08-frontend §5.1): ["channel", channelId, "messages", { era }]. */
export const chatKeys = {
  allMessages: (channelId: string) => ["channel", channelId, "messages"] as const,
  messages: (channelId: string, era: number | null) => ["channel", channelId, "messages", { era }] as const,
};

/** Pages of 50, the newest page first; each page is oldest first inside. */
export function useChatMessages(channelId: string, era: number | null) {
  return useInfiniteQuery({
    queryKey: chatKeys.messages(channelId, era),
    queryFn: async ({ pageParam }) => (await api.GET("/api/channels/{channelId}/messages", {
      params: { path: { channelId }, query: { before: pageParam, era: era ?? undefined, limit: 50 } },
    })).data ?? { items: [] },
    initialPageParam: undefined as number | undefined,
    getNextPageParam: (lastPage) => lastPage.nextBefore ?? undefined,
  });
}

/** Adds a message to the newest page unless it is there already (by id, or by nonce for my own). */
function addMessage(queryClient: QueryClient, channelId: string, message: ChatMessage) {
  queryClient.setQueriesData<InfiniteData<ChatPage>>({ queryKey: chatKeys.allMessages(channelId) }, (data) => {
    if (!data || data.pages.length === 0) return data;
    const known = data.pages.some((page) => page.items?.some((m) => m.id === message.id || m.clientNonce === message.clientNonce));
    if (known) return data;
    const [newest, ...older] = data.pages;
    return { ...data, pages: [{ ...newest, items: [...(newest?.items ?? []), message] }, ...older] };
  });
}

function replaceMessage(queryClient: QueryClient, channelId: string, message: ChatMessage) {
  queryClient.setQueriesData<InfiniteData<ChatPage>>({ queryKey: chatKeys.allMessages(channelId) }, (data) => data && {
    ...data,
    pages: data.pages.map((page) => ({ ...page, items: page.items?.map((m) => m.id === message.id ? message : m) })),
  });
}

/**
 * The room listens to its channel: new messages are appended once, edits and deletions replace theirs.
 * onCreated hears every new message (the room announces other people's to screen readers).
 */
export function useChatLive(channelId: string, onCreated?: (message: ChatMessage) => void) {
  const queryClient = useQueryClient();
  useTopic<ChatMessage>(channelId ? `/topic/channels/${channelId}/messages` : null, ({ type, payload }) => {
    if (type === "message.created") {
      addMessage(queryClient, channelId, payload);
      onCreated?.(payload);
    } else {
      replaceMessage(queryClient, channelId, payload);
    }
  });
}

/** POST with the nonce; a retry with the same nonce gets the same message back (never a second one). */
export function useSendMessage(channelId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (message: { body: string; clientNonce: string }) =>
      (await api.POST("/api/channels/{channelId}/messages", { params: { path: { channelId } }, body: message })).data as ChatMessage,
    onSuccess: (message) => addMessage(queryClient, channelId, message),
  });
}
