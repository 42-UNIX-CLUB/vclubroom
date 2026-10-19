import { type QueryClient, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/shared/api/client";
import type { components } from "@/shared/api/schema";

export type MemberSummary = components["schemas"]["MemberSummaryResponse"];
export type MembershipView = components["schemas"]["MembershipView"];
export type ClubPermission = NonNullable<MembershipView["permissions"]>[number];
export type JoinRequest = components["schemas"]["JoinRequestView"];
type JoinResponse = components["schemas"]["JoinResponse"];

/** Query keys of this feature (08-frontend §5.1): everything about one club lives under ["club", clubId]. */
export const membershipKeys = {
  summary: (clubId: string) => ["club", clubId, "member-summary"] as const,
  me: (clubId: string) => ["club", clubId, "membership", "me"] as const,
  joinRequests: (clubId: string) => ["club", clubId, "join-requests"] as const,
};

/** Member count and owners of a club, for the club page (any logged-in user). */
export function useMemberSummary(clubId: string) {
  return useQuery({
    queryKey: membershipKeys.summary(clubId),
    queryFn: async () =>
      (await api.GET("/api/clubs/{clubId}/member-summary", { params: { path: { clubId } } })).data as MemberSummary,
  });
}

/** My membership, roles, permissions and pending request in a club: drives which controls the UI shows. */
export function useMyMembership(clubId: string, enabled = true) {
  return useQuery({
    queryKey: membershipKeys.me(clubId),
    queryFn: async () =>
      (await api.GET("/api/clubs/{clubId}/membership/me", { params: { path: { clubId } } })).data as MembershipView,
    enabled,
  });
}

/** The queue of an APPROVAL club, oldest first (MEMBER_APPROVE only: pass enabled = can(…) to skip a sure 403). */
export function useJoinRequests(clubId: string, enabled = true) {
  return useQuery({
    queryKey: membershipKeys.joinRequests(clubId),
    queryFn: async () => (await api.GET("/api/clubs/{clubId}/join-requests", {
      params: { path: { clubId }, query: { status: "PENDING" } },
    })).data ?? [],
    enabled,
  });
}

/**
 * After joining, leaving or a decision, everything under ["club", clubId] is stale: my membership, the member count,
 * the join requests, later the spaces.
 */
function refreshClub(queryClient: QueryClient, clubId: string) {
  return queryClient.invalidateQueries({ queryKey: ["club", clubId] });
}

export function useJoinClub(clubId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async () =>
      (await api.POST("/api/clubs/{clubId}/join", { params: { path: { clubId } }, body: {} })).data as JoinResponse,
    onSuccess: () => refreshClub(queryClient, clubId),
  });
}

export function useLeaveClub(clubId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async () => {
      await api.DELETE("/api/clubs/{clubId}/members/me", { params: { path: { clubId } } });
    },
    onSuccess: () => refreshClub(queryClient, clubId),
  });
}

export function useCancelJoinRequest(clubId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async () => {
      await api.DELETE("/api/clubs/{clubId}/join-requests/mine", { params: { path: { clubId } } });
    },
    onSuccess: () => refreshClub(queryClient, clubId),
  });
}

export function useDecideJoinRequest(clubId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ requestId, approve }: { requestId: string; approve: boolean }) => {
      const params = { path: { clubId, requestId } };
      if (approve) await api.POST("/api/clubs/{clubId}/join-requests/{requestId}/approve", { params });
      else await api.POST("/api/clubs/{clubId}/join-requests/{requestId}/reject", { params });
    },
    onSuccess: () => refreshClub(queryClient, clubId),
  });
}
