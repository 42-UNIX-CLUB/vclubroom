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

export type Preference = components["schemas"]["PreferenceView"];
export type PreferenceChoice = components["schemas"]["PreferenceRequest"];

export const preferenceKeys = {
  mine: ["notification-preferences"] as const,
};

/** Every type with what applies to me and its defaults (R-15). */
export function usePreferences() {
  return useQuery({
    queryKey: preferenceKeys.mine,
    queryFn: async () => (await api.GET("/api/me/notification-preferences")).data ?? [],
  });
}

/**
 * The switches move at once and go back if the server refuses. Saves can overlap and finish in any order, so the list
 * is read again once the last one is done.
 */
export function useSavePreferences() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationKey: preferenceKeys.mine,
    mutationFn: async (choices: PreferenceChoice[]) => {
      await api.PUT("/api/me/notification-preferences", { body: choices });
    },
    onMutate: async (choices) => {
      await queryClient.cancelQueries({ queryKey: preferenceKeys.mine });
      const before = queryClient.getQueryData<Preference[]>(preferenceKeys.mine);
      queryClient.setQueryData<Preference[]>(preferenceKeys.mine, (list) => list?.map((p) => {
        const choice = choices.find((c) => c.type === p.type);
        return choice ? { ...p, inApp: choice.inApp, email: choice.email } : p;
      }));
      return { before };
    },
    onError: (_error, _choices, context) => queryClient.setQueryData(preferenceKeys.mine, context?.before),
    onSettled: () => {
      if (queryClient.isMutating({ mutationKey: preferenceKeys.mine }) === 1) {
        return queryClient.invalidateQueries({ queryKey: preferenceKeys.mine });
      }
    },
  });
}
