import { useQuery } from "@tanstack/react-query";
import { api } from "@/shared/api/client";
import type { components } from "@/shared/api/schema";

export type ClubCardData = components["schemas"]["ClubCardResponse"];
export type Landing = components["schemas"]["LandingResponse"];

/** Query keys of this feature (08-frontend §5.1): anonymous data lives under ["public"]. */
export const discoveryKeys = {
  landing: () => ["public", "landing"] as const,
};

/** Stats, most active and newest clubs, categories with counts: one request, no login needed. */
export function useLanding() {
  return useQuery({
    queryKey: discoveryKeys.landing(),
    queryFn: async () => (await api.GET("/api/public/landing")).data as Landing,
    staleTime: 60_000,
  });
}
