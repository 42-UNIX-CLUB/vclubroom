import { safeReturnTo } from "@/shared/lib/safeReturnTo";

const KEY = "returnTo";

/**
 * Login with 42 leaves the SPA (full-page redirect) and the backend always sends the user back to "/",
 * so the page to resume is kept in sessionStorage until PostLoginRedirect picks it up.
 */
export function rememberReturnTo(value: string | null): void {
  sessionStorage.setItem(KEY, safeReturnTo(value));
}

/** Signing in to an app (E20-S05) resumes on the backend's /oauth2/authorize: a full page load, not the router. */
export function resumesOnServer(path: string): boolean {
  return path.startsWith("/oauth2/authorize?");
}

/** Reads and forgets the remembered page (null if none). */
export function takeReturnTo(): string | null {
  const value = sessionStorage.getItem(KEY);
  sessionStorage.removeItem(KEY);
  return value ? safeReturnTo(value) : null;
}
