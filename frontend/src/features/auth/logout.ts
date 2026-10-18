import { csrfHeader } from "@/shared/api/client";

/** Spring Security's logout filter (POST /api/auth/logout) is not in the OpenAPI document, so plain fetch. */
export async function logout(): Promise<void> {
  const response = await fetch("/api/auth/logout", { method: "POST", credentials: "same-origin", headers: csrfHeader() });
  if (!response.ok) throw new Error(`Logout failed (HTTP ${response.status})`);
}