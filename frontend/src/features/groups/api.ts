import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/shared/api/client";
import type { components } from "@/shared/api/schema";

export type ClubGroup = components["schemas"]["ClubGroupView"];
export type GroupMember = components["schemas"]["GroupMemberView"];

/** Query keys of this feature (08-frontend §5.1): a club's groups live under ["club", clubId, "groups"]. */
export const groupKeys = {
  ofClub: (clubId: string) => ["club", clubId, "groups"] as const,
  members: (clubId: string, groupId: string) => ["club", clubId, "groups", groupId, "members"] as const,
};

/** The club's groups, by name, with their member counts (members and admins). */
export function useClubGroups(clubId: string) {
  return useQuery({
    queryKey: groupKeys.ofClub(clubId),
    queryFn: async () => (await api.GET("/api/clubs/{clubId}/groups", { params: { path: { clubId } } })).data ?? [],
    enabled: !!clubId,
  });
}

export function useGroupMembers(clubId: string, groupId: string, enabled = true) {
  return useQuery({
    queryKey: groupKeys.members(clubId, groupId),
    queryFn: async () => (await api.GET("/api/clubs/{clubId}/groups/{groupId}/members", {
      params: { path: { clubId, groupId } },
    })).data ?? [],
    enabled,
  });
}

/** Create (no groupId) or rename/describe a group. */
export function useSaveGroup(clubId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ groupId, name, description }: { groupId?: string; name?: string; description?: string }) => (groupId
      ? await api.PATCH("/api/clubs/{clubId}/groups/{groupId}", { params: { path: { clubId, groupId } }, body: { name, description } })
      : await api.POST("/api/clubs/{clubId}/groups", { params: { path: { clubId } }, body: { name, description } })).data as ClubGroup,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: groupKeys.ofClub(clubId) }),
  });
}

/** Deleting a group can close spaces (their rules lose it), so everything under ["club", clubId] is refreshed. */
export function useDeleteGroup(clubId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (groupId: string) => {
      await api.DELETE("/api/clubs/{clubId}/groups/{groupId}", { params: { path: { clubId, groupId } } });
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["club", clubId] }),
  });
}

/** Add (add = true) or remove someone; the counts and the member list follow. */
export function useGroupMembership(clubId: string, groupId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ userId, add }: { userId: string; add: boolean }) => {
      const params = { path: { clubId, groupId, userId } };
      if (add) await api.PUT("/api/clubs/{clubId}/groups/{groupId}/members/{userId}", { params });
      else await api.DELETE("/api/clubs/{clubId}/groups/{groupId}/members/{userId}", { params });
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: groupKeys.ofClub(clubId) }),
  });
}
