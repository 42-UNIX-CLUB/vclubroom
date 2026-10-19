import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { api } from "@/shared/api/client";
import type { components } from "@/shared/api/schema";

export type ClubCardData = components["schemas"]["ClubCardResponse"];
export type Landing = components["schemas"]["LandingResponse"];
export type PublicClub = components["schemas"]["ClubProfileResponse"];

export const PAGE_SIZE = 12;

/** What the directory shows; it lives in the URL query, so a filtered list can be shared and survives a reload. */
export interface DirectoryFilters {
  q?: string;
  category?: string;
  tag?: string;
  sort: string;
  status: "ACTIVE" | "ALL";
  page: number;
}

/** Query keys of this feature (08-frontend §5.1): anonymous data lives under ["public"]. */
export const discoveryKeys = {
  landing: () => ["public", "landing"] as const,
  categories: () => ["public", "categories"] as const,
  directory: (filters: DirectoryFilters) => ["public", "clubs", filters] as const,
  profile: (slug: string) => ["public", "club", slug] as const,
};

/** Stats, most active and newest clubs, categories with counts: one request, no login needed. */
export function useLanding() {
  return useQuery({
    queryKey: discoveryKeys.landing(),
    queryFn: async () => (await api.GET("/api/public/landing")).data as Landing,
    staleTime: 60_000,
  });
}

/** The fixed category list (R-20). Same key as the clubs feature's hook, so both share one cached answer. */
export function useCategories() {
  return useQuery({
    queryKey: discoveryKeys.categories(),
    queryFn: async () => (await api.GET("/api/public/categories")).data ?? [],
    staleTime: Infinity,
  });
}

/** One page of the directory. The previous page stays on screen while the next one loads (no flicker). */
export function useDirectory(filters: DirectoryFilters) {
  return useQuery({
    queryKey: discoveryKeys.directory(filters),
    queryFn: async () => (await api.GET("/api/public/clubs", {
      params: { query: { ...filters, size: PAGE_SIZE } },
    })).data,
    placeholderData: keepPreviousData,
  });
}

/** The public profile of an active or archived club (404 for any other). */
export function usePublicClub(slug: string) {
  return useQuery({
    queryKey: discoveryKeys.profile(slug),
    queryFn: async () => (await api.GET("/api/public/clubs/{slug}", { params: { path: { slug } } })).data as PublicClub,
  });
}
