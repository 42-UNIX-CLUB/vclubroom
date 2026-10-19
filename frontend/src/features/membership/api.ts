import { useQuery } from "@tanstack/react-query";
import { api } from "@/shared/api/client";
import type { components } from "@/shared/api/schema";

export type MemberSummary = components["schemas"]["MemberSummaryResponse"];

/** Query keys of this feature (08-frontend §5.1): everything about one club lives under ["club", clubId]. */
export const membershipKeys = {
  summary: (clubId: string) => ["club", clubId, "member-summary"] as const,
};

/** Member count and owners of a club, for the club page (any logged-in user). */
export function useMemberSummary(clubId: string) {
  return useQuery({
    queryKey: membershipKeys.summary(clubId),
    queryFn: async () =>
      (await api.GET("/api/clubs/{clubId}/member-summary", { params: { path: { clubId } } })).data as MemberSummary,
  });
}
