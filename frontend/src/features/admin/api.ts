import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/shared/api/client";
import type { components } from "@/shared/api/schema";

export type AdminClub = components["schemas"]["ClubResponse"];
export type ClubStatusFilter = "PENDING_APPROVAL" | "ACTIVE" | "REJECTED" | "ARCHIVED";

/** Query keys of the admin console (everything under ["admin"] is refreshed after an admin action). */
export const adminKeys = {
  all: () => ["admin"] as const,
  clubs: (status: ClubStatusFilter) => ["admin", "clubs", status] as const,
};

export function useAdminClubs(status: ClubStatusFilter) {
  return useQuery({
    queryKey: adminKeys.clubs(status),
    queryFn: async () => (await api.GET("/api/admin/clubs", { params: { query: { status, size: 100 } } })).data,
  });
}

/** Number shown on the "Approvals" badge of the admin navigation. */
export function usePendingClubCount(): number {
  return useAdminClubs("PENDING_APPROVAL").data?.totalElements ?? 0;
}

export function useApproveClub() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (clubId: string) =>
      (await api.POST("/api/admin/clubs/{clubId}/approve", { params: { path: { clubId } } })).data as AdminClub,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: adminKeys.all() }),
  });
}

/** Archiving changes the club page and the directory too, so their queries are refreshed with the console's. */
export function useArchiveClub() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ clubId, reason }: { clubId: string; reason: string }) =>
      (await api.POST("/api/admin/clubs/{clubId}/archive", { params: { path: { clubId } }, body: { reason } })).data as AdminClub,
    onSuccess: () => Promise.all([
      queryClient.invalidateQueries({ queryKey: adminKeys.all() }),
      queryClient.invalidateQueries({ queryKey: ["club"] }),
      queryClient.invalidateQueries({ queryKey: ["public"] }),
    ]),
  });
}

export function useRejectClub() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ clubId, reason }: { clubId: string; reason: string }) =>
      (await api.POST("/api/admin/clubs/{clubId}/reject", { params: { path: { clubId } }, body: { reason } })).data as AdminClub,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: adminKeys.all() }),
  });
}
