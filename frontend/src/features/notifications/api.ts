import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/shared/api/client";
import type { components } from "@/shared/api/schema";
import { useTopic } from "@/shared/realtime/RealtimeProvider";

export type Notification = components["schemas"]["NotificationView"];

/** Query keys of this feature (08-frontend §5.1): ["notifications"] and ["notifications", "unread-count"]. */
export const notificationKeys = {
  all: ["notifications"] as const,
  list: (unreadOnly: boolean) => ["notifications", { unreadOnly }] as const,
  unreadCount: ["notifications", "unread-count"] as const,
};

export function useUnreadCount() {
  return useQuery({
    queryKey: notificationKeys.unreadCount,
    queryFn: async () => (await api.GET("/api/notifications/unread-count")).data?.count ?? 0,
  });
}

/** Newest first, 20 per page. */
export function useNotifications(unreadOnly: boolean) {
  return useInfiniteQuery({
    queryKey: notificationKeys.list(unreadOnly),
    queryFn: async ({ pageParam }) => (await api.GET("/api/notifications", {
      params: { query: { unreadOnly, cursor: pageParam, limit: 20 } },
    })).data ?? { items: [] },
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
  });
}

/** A new notification arrives live: the badge and the lists ask again. */
export function useNotificationsLive() {
  const queryClient = useQueryClient();
  useTopic("/user/queue/notifications", () => void queryClient.invalidateQueries({ queryKey: notificationKeys.all }));
}

export function useMarkRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (notificationId: string) => {
      await api.POST("/api/notifications/{notificationId}/read", { params: { path: { notificationId } } });
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: notificationKeys.all }),
  });
}

export function useMarkAllRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async () => {
      await api.POST("/api/notifications/read-all");
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: notificationKeys.all }),
  });
}
