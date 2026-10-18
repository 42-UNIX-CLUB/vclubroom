import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/shared/api/client";
import type { components } from "@/shared/api/schema";

export type Club = components["schemas"]["ClubResponse"];
export type CreateClubInput = components["schemas"]["CreateClubRequest"];

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

/** My pending and rejected club requests, newest first. */
export function useMyClubRequests() {
  return useQuery({
    queryKey: clubKeys.myRequests(),
    queryFn: async () => (await api.GET("/api/me/club-requests")).data ?? [],
  });
}

export function useRequestClub() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (body: CreateClubInput) => (await api.POST("/api/clubs", { body })).data as Club,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: clubKeys.myRequests() }),
  });
}