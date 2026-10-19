import { useQuery } from "@tanstack/react-query";
import { api } from "@/shared/api/client";
import type { components } from "@/shared/api/schema";

export type Space = components["schemas"]["SpaceView"];
export type Channel = components["schemas"]["ChannelView"];

/** Query keys of this feature (08-frontend §5.1). Spaces sit under ["club", clubId], so joining refreshes them. */
export const spaceKeys = {
  ofClub: (clubId: string) => ["club", clubId, "spaces"] as const,
  channel: (channelId: string) => ["channel", channelId] as const,
};

/** The spaces of a club the viewer can read, with their channels and flags (the sidebar). */
export function useClubSpaces(clubId: string) {
  return useQuery({
    queryKey: spaceKeys.ofClub(clubId),
    queryFn: async () => (await api.GET("/api/clubs/{clubId}/spaces", { params: { path: { clubId } } })).data ?? [],
    enabled: !!clubId,
  });
}

/** One channel with the viewer's flags and the club's current era (the forum and chat views start here). */
export function useChannel(channelId: string, enabled = true) {
  return useQuery({
    queryKey: spaceKeys.channel(channelId),
    queryFn: async () =>
      (await api.GET("/api/channels/{channelId}", { params: { path: { channelId } } })).data as Channel,
    enabled,
  });
}
