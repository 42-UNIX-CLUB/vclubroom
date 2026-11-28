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

export type App = components["schemas"]["AppView"];
export type RegisteredApp = components["schemas"]["RegisteredApp"];
export type AppInput = { name: string; description: string; homepageUrl: string; redirectUris: string[]; confidential: boolean };

/** The OAuth apps I registered (E20-S04). A confidential app's secret only comes in the answer that makes it. */
const appKeys = { mine: ["apps"] as const };

export function useApps() {
  return useQuery({
    queryKey: appKeys.mine,
    queryFn: async () => (await api.GET("/api/me/apps")).data ?? [],
  });
}

export function useRegisterApp() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (body: AppInput) => (await api.POST("/api/me/apps", { body })).data as RegisteredApp,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: appKeys.mine }),
  });
}

export function useUpdateApp() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ appId, ...body }: AppInput & { appId: string }) =>
      (await api.PATCH("/api/me/apps/{appId}", { params: { path: { appId } }, body })).data as App,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: appKeys.mine }),
  });
}

export function useNewAppSecret() {
  return useMutation({
    mutationFn: async (appId: string) =>
      (await api.POST("/api/me/apps/{appId}/secret", { params: { path: { appId } } })).data as RegisteredApp,
  });
}

export function useDeleteApp() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (appId: string) => {
      await api.DELETE("/api/me/apps/{appId}", { params: { path: { appId } } });
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: appKeys.mine }),
  });
}

export type ConsentRequest = components["schemas"]["ConsentRequestView"];
export type AuthorizedApp = components["schemas"]["AuthorizedAppView"];

/** What the consent page shows about an app asking to be let in (E20-S05). */
export function useConsentRequest(clientId: string, scope: string) {
  return useQuery({
    queryKey: ["oauth-consent", clientId, scope],
    queryFn: async () =>
      (await api.GET("/api/oauth/consent", { params: { query: { client_id: clientId, scope } } })).data as ConsentRequest,
    enabled: clientId !== "",
    retry: false,
  });
}

/**
 * Answers the authorization server as a consent form would, but with fetch: a form couldn't follow its redirect to the
 * app (the CSP's form-action). It answers {redirectUri}: back to the app with a code, or access_denied when nothing
 * is allowed.
 */
export async function answerConsent(clientId: string, state: string, scopes: string[]): Promise<string> {
  const body = new URLSearchParams({ client_id: clientId, state });
  for (const scope of scopes) body.append("scope", scope);
  const response = await fetch("/oauth2/authorize", {
    method: "POST",
    credentials: "same-origin",
    headers: { Accept: "application/json" },
    body,
  });
  if (!response.ok) throw new Error("This request has expired: go back to the app and sign in again.");
  return ((await response.json()) as { redirectUri: string }).redirectUri;
}

/** The apps I let in (E20-S05). */
const authorizedKeys = { mine: ["authorized-apps"] as const };

export function useAuthorizedApps() {
  return useQuery({
    queryKey: authorizedKeys.mine,
    queryFn: async () => (await api.GET("/api/me/authorized-apps")).data ?? [],
  });
}

export function useRevokeAuthorizedApp() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (appId: string) => {
      await api.DELETE("/api/me/authorized-apps/{appId}", { params: { path: { appId } } });
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: authorizedKeys.mine }),
  });
}
