import { useQuery } from "@tanstack/react-query";
import { api } from "@/shared/api/client";
import { ApiError } from "@/shared/api/errors";
import type { components } from "@/shared/api/schema";

export type Me = components["schemas"]["MeResponse"];

/** The logged-in user; null = anonymous visitor (401), never an error. */
export function useMe() {
  return useQuery({
    queryKey: ["me"],
    queryFn: async (): Promise<Me | null> => {
      try {
        const { data } = await api.GET("/api/me");
        return data ?? null;
      } catch (error) {
        if (error instanceof ApiError && error.status === 401) return null;
        throw error;
      }
    },
    staleTime: 5 * 60_000,
  });
}
