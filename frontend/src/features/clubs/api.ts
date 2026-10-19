import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/shared/api/client";
import type { components } from "@/shared/api/schema";

export type Club = components["schemas"]["ClubResponse"];
export type CreateClubInput = components["schemas"]["CreateClubRequest"];
export type UpdateClubInput = components["schemas"]["UpdateClubRequest"];

/** Query keys of this feature (08-frontend §5.1). */
export const clubKeys = {
  detail: (slugOrId: string) => ["club", slugOrId] as const,
  myRequests: () => ["me", "club-requests"] as const,
  categories: () => ["public", "categories"] as const,
};

/** The fixed category list (R-20), served by the discovery module. */
export function useCategories() {
  return useQuery({
    queryKey: clubKeys.categories(),
    queryFn: async () => (await api.GET("/api/public/categories")).data ?? [],
    staleTime: Infinity,   // configuration, changes only with a deployment
  });
}

/**
 * One club by slug or id. 404 when it does not exist or the viewer may not see it
 * (a club waiting for approval is visible to its founder and the admins only).
 */
export function useClub(slugOrId: string) {
  return useQuery({
    queryKey: clubKeys.detail(slugOrId),
    queryFn: async () =>
      (await api.GET("/api/clubs/{idOrSlug}", { params: { path: { idOrSlug: slugOrId } } })).data as Club,
  });
}

/** My pending and rejected club requests, newest first. */
export function useMyClubRequests() {
  return useQuery({
    queryKey: clubKeys.myRequests(),
    queryFn: async () => (await api.GET("/api/me/club-requests")).data ?? [],
  });
}

/**
 * Club settings › Profile (E04-S04). The club page, its membership data (switching to OPEN admits everyone waiting)
 * and the directory all change, so their queries are refreshed.
 */
export function useUpdateClub(clubId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (body: UpdateClubInput) =>
      (await api.PATCH("/api/clubs/{clubId}", { params: { path: { clubId } }, body })).data as Club,
    onSuccess: () => Promise.all([
      queryClient.invalidateQueries({ queryKey: ["club"] }),
      queryClient.invalidateQueries({ queryKey: ["public"] }),
    ]),
  });
}

/** Ask to bring an archived club back (E14-S02); an admin decides. */
export function useRequestRevival(clubId: string) {
  return useMutation({
    mutationFn: async (motivation: string) => (await api.POST("/api/clubs/{clubId}/revival-requests", {
      params: { path: { clubId } }, body: { motivation },
    })).data,
  });
}

export function useRequestClub() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (body: CreateClubInput) => (await api.POST("/api/clubs", { body })).data as Club,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: clubKeys.myRequests() }),
  });
}
