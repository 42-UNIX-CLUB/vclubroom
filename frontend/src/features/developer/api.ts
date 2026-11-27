import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/shared/api/client";
import type { components } from "@/shared/api/schema";

export type Token = components["schemas"]["TokenView"];
export type CreatedToken = components["schemas"]["CreatedToken"];

/** My personal access tokens (E20-S02). Their values never come back: only in the answer to creating one. */
const tokenKeys = { mine: ["tokens"] as const };

export function useTokens() {
  return useQuery({
    queryKey: tokenKeys.mine,
    queryFn: async () => (await api.GET("/api/me/tokens")).data ?? [],
  });
}

export function useCreateToken() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (body: { name: string; scopes: string[]; expiresAt: string | null }) =>
      (await api.POST("/api/me/tokens", { body: { ...body, expiresAt: body.expiresAt ?? undefined } })).data as CreatedToken,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: tokenKeys.mine }),
  });
}

export function useRevokeToken() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (tokenId: string) => {
      await api.DELETE("/api/me/tokens/{tokenId}", { params: { path: { tokenId } } });
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: tokenKeys.mine }),
  });
}
