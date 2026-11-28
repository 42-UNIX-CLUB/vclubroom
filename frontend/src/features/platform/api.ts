import { useQuery } from "@tanstack/react-query";
import { api } from "@/shared/api/client";
import type { components } from "@/shared/api/schema";

export type PublicStatus = components["schemas"]["PublicStatus"];
export type ServiceState = NonNullable<PublicStatus["status"]>;
export type Changelog = components["schemas"]["Changelog"];
export type Release = components["schemas"]["Release"];

/** Query keys of this feature (08-frontend §5.1). */
export const platformKeys = {
  status: ["public", "status"] as const,
  changelog: ["public", "changelog"] as const,
};

/** The public status (E19-S02), asked again every 30 s while the page is open. */
export function usePublicStatus() {
  return useQuery({
    queryKey: platformKeys.status,
    queryFn: async () => (await api.GET("/api/public/status")).data as PublicStatus,
    refetchInterval: 30_000,
  });
}

/** The changelog (E19-S03). It only changes with a deploy: asked once per visit. */
export function useChangelog() {
  return useQuery({
    queryKey: platformKeys.changelog,
    queryFn: async () => (await api.GET("/api/public/changelog")).data as Changelog,
    staleTime: Infinity,
  });
}
