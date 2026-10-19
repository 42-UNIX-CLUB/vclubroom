import { keepPreviousData, type QueryClient, useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/shared/api/client";
import type { components } from "@/shared/api/schema";

export type MemberSummary = components["schemas"]["MemberSummaryResponse"];
export type MembershipView = components["schemas"]["MembershipView"];
export type ClubPermission = NonNullable<MembershipView["permissions"]>[number];
export type JoinRequest = components["schemas"]["JoinRequestView"];
type JoinResponse = components["schemas"]["JoinResponse"];
export type OwnershipInvitation = components["schemas"]["InvitationView"];
export type UserSummary = components["schemas"]["UserSummary"];
export type Member = components["schemas"]["MemberResponse"];

/** Query keys of this feature (08-frontend §5.1): everything about one club lives under ["club", clubId]. */
export const membershipKeys = {
  summary: (clubId: string) => ["club", clubId, "member-summary"] as const,
  me: (clubId: string) => ["club", clubId, "membership", "me"] as const,
  joinRequests: (clubId: string) => ["club", clubId, "join-requests"] as const,
  members: (clubId: string, query: string) => ["club", clubId, "members", { query }] as const,
  clubInvitations: (clubId: string) => ["club", clubId, "ownership-invitations"] as const,
  myInvitations: ["me", "ownership-invitations"] as const,
  userSearch: (query: string) => ["users", { query }] as const,
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

/** Who is in the club (members only), owners first, 50 per page; query searches names and logins. */
export function useMembers(clubId: string, query: string) {
  const q = query.trim();
  return useInfiniteQuery({
    queryKey: membershipKeys.members(clubId, q),
    queryFn: async ({ pageParam }) => (await api.GET("/api/clubs/{clubId}/members", {
      params: { path: { clubId }, query: { query: q || undefined, page: pageParam, size: 50 } },
    })).data!,
    initialPageParam: 0,
    getNextPageParam: (last) => ((last.page ?? 0) + 1) * (last.size ?? 50) < (last.totalElements ?? 0) ? (last.page ?? 0) + 1 : undefined,
    placeholderData: keepPreviousData,
  });
}

export function useRemoveMember(clubId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (userId: string) => {
      await api.DELETE("/api/clubs/{clubId}/members/{userId}", { params: { path: { clubId, userId } } });
    },
    onSuccess: () => refreshClub(queryClient, clubId),
  });
}

/** The club's members who are not owners yet, for the ownership invitation (only members can become owners). */
export function useMemberSearch(clubId: string, query: string) {
  const q = query.trim();
  return useQuery({
    queryKey: [...membershipKeys.members(clubId, q), "picker"] as const,
    queryFn: async () => ((await api.GET("/api/clubs/{clubId}/members", {
      params: { path: { clubId }, query: { query: q, size: 8 } },
    })).data?.items ?? [])
      .filter((member) => !member.owner)
      .map((member): UserSummary => ({ id: member.userId, login: member.login, displayName: member.displayName, avatarUrl: member.avatarUrl })),
    enabled: q.length >= 2,
    placeholderData: keepPreviousData,
  });
}

/**
 * After joining, leaving or a decision, everything under ["club", clubId] is stale: my membership, the member count,
 * the join requests, the member list, later the spaces.
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

/** People whose login starts with, or whose name contains, the query; nothing is asked below 2 characters. */
export function useUserSearch(query: string) {
  const q = query.trim();
  return useQuery({
    queryKey: membershipKeys.userSearch(q),
    queryFn: async () => (await api.GET("/api/users", { params: { query: { query: q, limit: 8 } } })).data ?? [],
    enabled: q.length >= 2,
    placeholderData: keepPreviousData,
  });
}

/** Ownership invitations waiting for my answer, in any club. */
export function useMyInvitations(enabled = true) {
  return useQuery({
    queryKey: membershipKeys.myInvitations,
    queryFn: async () => (await api.GET("/api/me/ownership-invitations")).data ?? [],
    enabled,
  });
}

/** What the club's owners have sent and is still waiting (owners only). */
export function useClubInvitations(clubId: string, enabled = true) {
  return useQuery({
    queryKey: membershipKeys.clubInvitations(clubId),
    queryFn: async () =>
      (await api.GET("/api/clubs/{clubId}/ownership-invitations", { params: { path: { clubId } } })).data ?? [],
    enabled,
  });
}

/** transfer = true: "transfer ownership", the inviter steps down when the invitee accepts. */
export function useInviteOwner(clubId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (body: { inviteeId: string; transfer: boolean }) => {
      await api.POST("/api/clubs/{clubId}/ownership-invitations", { params: { path: { clubId } }, body });
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: membershipKeys.clubInvitations(clubId) }),
  });
}

export function useRevokeInvitation(clubId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (invitationId: string) => {
      await api.DELETE("/api/ownership-invitations/{invitationId}", { params: { path: { invitationId } } });
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: membershipKeys.clubInvitations(clubId) }),
  });
}

/** Accepting changes the club (its owners, my roles), so its queries are refreshed along with my inbox. */
export function useAnswerInvitation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ invitation, accept }: { invitation: OwnershipInvitation; accept: boolean }) => {
      const params = { path: { invitationId: invitation.id ?? "" } };
      if (accept) await api.POST("/api/ownership-invitations/{invitationId}/accept", { params });
      else await api.POST("/api/ownership-invitations/{invitationId}/decline", { params });
    },
    onSuccess: (_, { invitation }) => Promise.all([
      queryClient.invalidateQueries({ queryKey: membershipKeys.myInvitations }),
      refreshClub(queryClient, invitation.clubId ?? ""),
    ]),
  });
}

export function useStepDown(clubId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async () => {
      await api.POST("/api/clubs/{clubId}/owners/me/step-down", { params: { path: { clubId } } });
    },
    // Not awaited: the Ownership tab disappears with the owner role, and the caller's onSuccess must still run.
    onSuccess: () => {
      void refreshClub(queryClient, clubId);
    },
  });
}
