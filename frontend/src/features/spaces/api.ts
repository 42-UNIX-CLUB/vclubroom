import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/shared/api/client";
import type { components } from "@/shared/api/schema";

export type Space = components["schemas"]["SpaceView"];
export type Channel = components["schemas"]["ChannelView"];
export type Rule = components["schemas"]["RuleRequest"];
export type AccessLevel = Rule["level"];

/** Query keys of this feature (08-frontend §5.1). Spaces sit under ["club", clubId], so joining refreshes them. */
export const spaceKeys = {
  ofClub: (clubId: string) => ["club", clubId, "spaces"] as const,
  managed: (clubId: string) => ["club", clubId, "spaces", { includeArchived: true }] as const,
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

/** Club settings › Spaces: archived spaces and channels too. */
export function useManagedSpaces(clubId: string) {
  return useQuery({
    queryKey: spaceKeys.managed(clubId),
    queryFn: async () => (await api.GET("/api/clubs/{clubId}/spaces", {
      params: { path: { clubId }, query: { includeArchived: true } },
    })).data ?? [],
    enabled: !!clubId,
  });
}

/**
 * Every change to spaces or channels refreshes the club's spaces (the sidebar and the settings) and the open
 * channels (their names and flags).
 */
function useSpaceMutation<V>(clubId: string, run: (variables: V) => Promise<unknown>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: run,
    onSuccess: () => Promise.all([
      queryClient.invalidateQueries({ queryKey: spaceKeys.ofClub(clubId) }),
      queryClient.invalidateQueries({ queryKey: ["channel"] }),
    ]),
  });
}

export type SpaceInput = { name?: string; description?: string; position?: number; readRule?: Rule; writeRule?: Rule };

export function useSaveSpace(clubId: string) {
  return useSpaceMutation(clubId, async ({ spaceId, ...body }: SpaceInput & { spaceId?: string }) => {
    if (spaceId) await api.PATCH("/api/spaces/{spaceId}", { params: { path: { spaceId } }, body });
    else await api.POST("/api/clubs/{clubId}/spaces", {
      params: { path: { clubId } }, body: { name: body.name ?? "", description: body.description, readRule: body.readRule!, writeRule: body.writeRule! },
    });
  });
}

export function useArchiveSpace(clubId: string) {
  return useSpaceMutation(clubId, async (spaceId: string) => {
    await api.POST("/api/spaces/{spaceId}/archive", { params: { path: { spaceId } } });
  });
}

export function useAddChannel(clubId: string) {
  return useSpaceMutation(clubId, async ({ spaceId, type, name }: { spaceId: string; type: "FORUM" | "CHAT"; name: string }) => {
    await api.POST("/api/spaces/{spaceId}/channels", { params: { path: { spaceId } }, body: { type, name } });
  });
}

export function useUpdateChannel(clubId: string) {
  return useSpaceMutation(clubId, async ({ channelId, ...body }: { channelId: string; name?: string; position?: number }) => {
    await api.PATCH("/api/channels/{channelId}", { params: { path: { channelId } }, body });
  });
}

export function useArchiveChannel(clubId: string) {
  return useSpaceMutation(clubId, async (channelId: string) => {
    await api.POST("/api/channels/{channelId}/archive", { params: { path: { channelId } } });
  });
}
